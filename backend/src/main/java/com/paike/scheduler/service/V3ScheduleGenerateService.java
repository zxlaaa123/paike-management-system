package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.dto.MultipleScheduleGenerateRequest;
import com.paike.scheduler.service.dto.ScheduleGenerateRequest;
import com.paike.scheduler.service.dto.ScheduleGenerateResult;
import com.paike.scheduler.service.scheduling.DeltaPenaltyScorer;
import com.paike.scheduler.service.scheduling.RuleConfig;
import com.paike.scheduler.service.scheduling.SchedulingReferenceData;
import com.paike.scheduler.service.scheduling.SchedulingReferenceLoader;
import com.paike.scheduler.service.scheduling.SchedulingSupport;
import com.paike.scheduler.service.scheduling.ScoringFunctions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class V3ScheduleGenerateService {

    private static final String DEFAULT_STRATEGY = "COMPREHENSIVE";
    private static final DateTimeFormatter PLAN_NAME_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final boolean USE_DELTA_PENALTY_SCORING = false;

    private final SemesterService semesterService;
    private final ScheduleRuleService ruleService;
    private final ScheduleScoreService scoreService;
    private final SchedulePlanMapper planMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final SchedulingReferenceLoader referenceLoader;
    private final SchedulePlanExplainService explainService;
    private final ScheduleThresholdProperties thresholdProperties;

    @Transactional(rollbackFor = Exception.class)
    public ScheduleGenerateResult generate(ScheduleGenerateRequest request) {
        Long semesterId = resolveSemesterId(request.getSemesterId());
        String strategyType = normalizeStrategyType(request.getStrategyType());
        String planName = resolvePlanName(request.getPlanName(), strategyType);
        boolean overwriteDraft = Boolean.TRUE.equals(request.getOverwriteDraft());

        prepareDraftTarget(semesterId, planName, overwriteDraft);

        List<TeachingTask> tasks = loadTeachingTasks(semesterId);
        if (tasks.isEmpty()) {
            throw new BusinessException("当前学期没有可排课的教学任务");
        }

        SchedulingReferenceData refData = referenceLoader.loadForV3Generate(semesterId, strategyType);
        RuleConfig rules = loadRuleConfig();

        SchedulePlan plan = new SchedulePlan();
        plan.setSemesterId(semesterId);
        plan.setName(planName);
        plan.setStrategyType(strategyType);
        plan.setStatus("DRAFT");
        plan.setScheduledCount(0);
        plan.setUnscheduledCount(0);
        plan.setConflictCount(0);
        plan.setDescription("V3 自动排课生成方案");
        plan.setGeneratedBy("V3_GENERATE");
        plan.setGeneratedAt(LocalDateTime.now());
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        explainService.clearPlanArtifacts(plan.getId());

        StepCounter stepCounter = new StepCounter();
        explainService.appendGenerateLog(
                plan.getId(),
                semesterId,
                null,
                "INFO",
                "START_GENERATE",
                "开始生成" + plan.getName(),
                stepCounter.next());
        explainService.appendGenerateLog(
                plan.getId(),
                semesterId,
                null,
                "INFO",
                "LOAD_TASK",
                "读取当前学期教学任务，共 " + tasks.size() + " 条",
                stepCounter.next());

        List<SchedulePlanItem> generatedItems = generatePlanItems(plan, tasks, refData, rules);
        for (SchedulePlanItem item : generatedItems) {
            planItemMapper.insert(item);
        }

        plan.setScheduledCount(generatedItems.size());
        explainService.appendGenerateLog(
                plan.getId(),
                semesterId,
                null,
                "INFO",
                "GENERATE_SCORE",
                "开始生成评分，当前已排 " + plan.getScheduledCount() + " 条，未排 " + plan.getUnscheduledCount() + " 条",
                stepCounter.next());
        scoreService.rescore(plan);
        explainService.appendGenerateLog(
                plan.getId(),
                semesterId,
                null,
                "INFO",
                "FINISH_GENERATE",
                "方案生成完成，总分 " + plan.getTotalScore() + "，冲突数 " + plan.getConflictCount(),
                stepCounter.next());

        return toResult(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ScheduleGenerateResult> generateMultiple(MultipleScheduleGenerateRequest request) {
        Long semesterId = resolveSemesterId(request.getSemesterId());
        boolean overwriteDraft = Boolean.TRUE.equals(request.getOverwriteDraft());
        List<String> strategyTypes = request.getStrategyTypes();
        if (strategyTypes == null || strategyTypes.isEmpty()) {
            strategyTypes = List.of("TEACHER_PRIORITY", "CLASS_BALANCE", "CLASSROOM_UTILIZATION", DEFAULT_STRATEGY);
        }

        List<ScheduleGenerateResult> results = new ArrayList<>();
        String suffix = LocalDateTime.now().format(PLAN_NAME_SUFFIX);
        for (String strategyType : strategyTypes) {
            ScheduleGenerateRequest single = new ScheduleGenerateRequest();
            single.setSemesterId(semesterId);
            single.setStrategyType(strategyType);
            single.setOverwriteDraft(overwriteDraft);
            single.setPlanName(strategyLabel(normalizeStrategyType(strategyType)) + "方案-" + suffix);
            results.add(generate(single));
        }
        return results;
    }

    private Long resolveSemesterId(Long semesterId) {
        if (semesterId != null) {
            return semesterId;
        }
        return semesterService.getCurrentSemester().getId();
    }

    private String normalizeStrategyType(String strategyType) {
        if (strategyType == null || strategyType.isBlank()) {
            return DEFAULT_STRATEGY;
        }
        return strategyType.trim();
    }

    private String resolvePlanName(String planName, String strategyType) {
        if (planName != null && !planName.isBlank()) {
            return planName.trim();
        }
        return strategyLabel(strategyType) + "方案-" + LocalDateTime.now().format(PLAN_NAME_SUFFIX);
    }

    private String strategyLabel(String strategyType) {
        return switch (strategyType) {
            case "TEACHER_PRIORITY" -> "教师优先";
            case "CLASS_BALANCE" -> "班级均衡";
            case "CLASSROOM_UTILIZATION" -> "教室利用率";
            default -> "综合最优";
        };
    }

    private void prepareDraftTarget(Long semesterId, String planName, boolean overwriteDraft) {
        List<SchedulePlan> existingDrafts = planMapper.selectList(
                new LambdaQueryWrapper<SchedulePlan>()
                        .eq(SchedulePlan::getSemesterId, semesterId)
                        .eq(SchedulePlan::getName, planName)
                        .eq(SchedulePlan::getStatus, "DRAFT"));
        if (existingDrafts.isEmpty()) {
            return;
        }
        if (!overwriteDraft) {
            throw new BusinessException("已存在同名草稿方案，请修改方案名称或启用覆盖草稿");
        }
        for (SchedulePlan draft : existingDrafts) {
            planItemMapper.delete(new LambdaQueryWrapper<SchedulePlanItem>().eq(SchedulePlanItem::getPlanId, draft.getId()));
            explainService.clearPlanArtifacts(draft.getId());
            planMapper.deleteById(draft.getId());
        }
    }

    private List<TeachingTask> loadTeachingTasks(Long semesterId) {
        return teachingTaskMapper.selectList(
                new LambdaQueryWrapper<TeachingTask>()
                        .eq(TeachingTask::getSemesterId, semesterId)
                        .eq(TeachingTask::getStatus, 1)
                        .eq(TeachingTask::getDeleted, 0));
    }

    private RuleConfig loadRuleConfig() {
        return new RuleConfig(
                ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS"),
                ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS"),
                ruleService.getBoolValue("ALLOW_SAME_COURSE_SAME_DAY"));
    }

    private List<SchedulePlanItem> generatePlanItems(SchedulePlan plan, List<TeachingTask> tasks, SchedulingReferenceData refData, RuleConfig rules) {
        List<TeachingTask> sortedTasks = SchedulingSupport.sortTasks(tasks, refData.unavailableCountByTeacher(), refData.courseMap(), refData.classMap());
        List<SchedulePlanItem> generatedItems = new ArrayList<>();
        int unscheduledCount = 0;
        StepCounter stepCounter = new StepCounter(2);

        for (TeachingTask task : sortedTasks) {
            int requiredSlots = Math.max(1, (int) Math.ceil((task.getWeeklyHours() == null ? 0 : task.getWeeklyHours()) / 2.0));
            Set<Integer> usedDays = new HashSet<>();
            String courseType = SchedulingSupport.getCourseType(task.getCourseId(), refData.courseMap());
            int studentCount = SchedulingSupport.getClassStudentCount(task.getClassId(), refData.classMap());
            String taskLabel = taskLabel(task, refData.courseMap(), refData.classMap());
            List<Classroom> matchedRooms = refData.classrooms().stream()
                    .filter(room -> room.getCapacity() != null && room.getCapacity() >= studentCount)
                    .filter(room -> SchedulingSupport.isRoomTypeMatched(courseType, room.getRoomType()))
                    .sorted(Comparator.comparingInt(Classroom::getCapacity))
                    .toList();

            explainService.appendGenerateLog(
                    plan.getId(),
                    plan.getSemesterId(),
                    task.getId(),
                    "INFO",
                    "CHECK_CLASSROOM",
                    "教学任务：" + taskLabel + " 可用候选教室 " + matchedRooms.size() + " 个",
                    stepCounter.next());

            if (matchedRooms.isEmpty()) {
                unscheduledCount += requiredSlots;
                UnassignedReason reason = buildNoMatchedRoomReason(task, studentCount, courseType, refData.classrooms());
                explainService.saveUnassignedTask(plan.getId(), plan.getSemesterId(), task.getId(),
                        reason.reasonCode(), reason.reasonMessage(), reason.suggestion());
                explainService.appendGenerateLog(
                        plan.getId(),
                        plan.getSemesterId(),
                        task.getId(),
                        "ERROR",
                        "ASSIGN_FAILED",
                        "教学任务：" + taskLabel + " 排课失败，原因：" + reason.reasonMessage(),
                        stepCounter.next());
                continue;
            }

            for (int occurrence = 0; occurrence < requiredSlots; occurrence++) {
                Candidate candidate = findBestCandidate(plan, task, usedDays, matchedRooms, generatedItems, refData, rules, stepCounter);
                if (candidate == null) {
                    unscheduledCount++;
                    UnassignedReason reason = analyzeUnassignedReason(task, usedDays, matchedRooms, generatedItems, refData, rules);
                    explainService.saveUnassignedTask(plan.getId(), plan.getSemesterId(), task.getId(),
                            reason.reasonCode(), reason.reasonMessage(), reason.suggestion());
                    explainService.appendGenerateLog(
                            plan.getId(),
                            plan.getSemesterId(),
                            task.getId(),
                            "ERROR",
                            "ASSIGN_FAILED",
                            "教学任务：" + taskLabel + " 排课失败，原因：" + reason.reasonMessage(),
                            stepCounter.next());
                    continue;
                }

                explainService.appendGenerateLog(
                        plan.getId(),
                        plan.getSemesterId(),
                        task.getId(),
                        "INFO",
                        "ASSIGN_SUCCESS",
                        "教学任务：" + taskLabel + " 排课成功，安排至"
                                + candidate.slot().getTimeLabel() + "，教室 " + candidate.room().getRoomName()
                                + "，候选分 " + String.format(Locale.ROOT, "%.2f", candidate.score()),
                        stepCounter.next());
                generatedItems.add(toPlanItem(plan.getId(), task, candidate.slot(), candidate.room()));
                usedDays.add(candidate.slot().getDayOfWeek());
            }
        }

        plan.setScheduledCount(generatedItems.size());
        plan.setUnscheduledCount(unscheduledCount);
        plan.setConflictCount(0);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return generatedItems;
    }

    private Candidate findBestCandidate(
            SchedulePlan plan,
            TeachingTask task,
            Set<Integer> usedDays,
            List<Classroom> matchedRooms,
            List<SchedulePlanItem> generatedItems,
            SchedulingReferenceData refData,
            RuleConfig rules,
            StepCounter stepCounter
    ) {
        Candidate best = null;
        String taskLabel = taskLabel(task, refData.courseMap(), refData.classMap());
        for (TimeSlot slot : refData.sortedTimeSlots()) {
            if (refData.unavailableKeySet().contains(task.getTeacherId() + "_" + slot.getId())) {
                explainService.appendGenerateLog(plan.getId(), plan.getSemesterId(), task.getId(), "WARN", "CHECK_TEACHER",
                        "教学任务：" + taskLabel + " 跳过 " + slot.getTimeLabel() + "，教师禁排", stepCounter.next());
                continue;
            }
            if (!checkTeacherDailyLimit(task.getTeacherId(), slot.getDayOfWeek(), rules.teacherMaxDailySlots(), generatedItems)) {
                explainService.appendGenerateLog(plan.getId(), plan.getSemesterId(), task.getId(), "WARN", "CHECK_TEACHER",
                        "教学任务：" + taskLabel + " 跳过 " + slot.getTimeLabel() + "，教师日排课上限", stepCounter.next());
                continue;
            }
            if (!checkClassDailyLimit(task.getClassId(), slot.getDayOfWeek(), rules.classMaxDailySlots(), generatedItems)) {
                explainService.appendGenerateLog(plan.getId(), plan.getSemesterId(), task.getId(), "WARN", "CHECK_CLASS",
                        "教学任务：" + taskLabel + " 跳过 " + slot.getTimeLabel() + "，班级日排课上限", stepCounter.next());
                continue;
            }
            if (!rules.allowSameCourseSameDay() && usedDays.contains(slot.getDayOfWeek())) {
                explainService.appendGenerateLog(plan.getId(), plan.getSemesterId(), task.getId(), "WARN", "CHECK_CLASS",
                        "教学任务：" + taskLabel + " 跳过 " + slot.getTimeLabel() + "，同任务已占用同一天", stepCounter.next());
                continue;
            }
            if (!rules.allowSameCourseSameDay() && hasSameCourseSameDay(task.getClassId(), task.getCourseId(), slot.getDayOfWeek(), generatedItems)) {
                explainService.appendGenerateLog(plan.getId(), plan.getSemesterId(), task.getId(), "WARN", "CHECK_CLASS",
                        "教学任务：" + taskLabel + " 跳过 " + slot.getTimeLabel() + "，同班同课程同日限制", stepCounter.next());
                continue;
            }

            best = evaluateRoomsForSlot(plan, task, slot, matchedRooms, generatedItems, refData, taskLabel, best, stepCounter);
        }
        return best;
    }

    /**
     * 在给定 slot 下扫一遍候选教室，遇冲突跳过、否则评分并与 currentBest 比较取更高。
     * 抽出此层是因为外层 slot loop 已经长，进一步把 room 评分提到独立方法降低嵌套。
     */
    private Candidate evaluateRoomsForSlot(
            SchedulePlan plan,
            TeachingTask task,
            TimeSlot slot,
            List<Classroom> matchedRooms,
            List<SchedulePlanItem> generatedItems,
            SchedulingReferenceData refData,
            String taskLabel,
            Candidate currentBest,
            StepCounter stepCounter
    ) {
        Candidate best = currentBest;
        for (Classroom room : matchedRooms) {
            if (hasConflict(task, slot, room, generatedItems)) {
                explainService.appendGenerateLog(plan.getId(), plan.getSemesterId(), task.getId(), "WARN", "CHECK_CLASSROOM",
                        "教学任务：" + taskLabel + " 跳过候选 " + slot.getTimeLabel() + " / " + room.getRoomName() + "，资源冲突", stepCounter.next());
                continue;
            }
            double score = scoreCandidate(task, slot, room, generatedItems, refData);
            explainService.appendGenerateLog(plan.getId(), plan.getSemesterId(), task.getId(), "INFO", "CALCULATE_SCORE",
                    "候选位置：" + slot.getTimeLabel() + "，教室 " + room.getRoomName() + "，得分 " + String.format(Locale.ROOT, "%.2f", score),
                    stepCounter.next());
            if (best == null || score > best.score()) {
                best = new Candidate(slot, room, score);
            }
        }
        return best;
    }

    private boolean hasConflict(TeachingTask task, TimeSlot slot, Classroom room, List<SchedulePlanItem> generatedItems) {
        for (SchedulePlanItem item : generatedItems) {
            if (!Objects.equals(item.getWeekday(), slot.getDayOfWeek())) {
                continue;
            }
            if (Objects.equals(item.getTeacherId(), task.getTeacherId()) && Objects.equals(item.getStartPeriod(), ScoringFunctions.slotToStartPeriod(slot))) {
                return true;
            }
            if (Objects.equals(item.getClassId(), task.getClassId()) && Objects.equals(item.getStartPeriod(), ScoringFunctions.slotToStartPeriod(slot))) {
                return true;
            }
            if (Objects.equals(item.getClassroomId(), room.getId()) && Objects.equals(item.getStartPeriod(), ScoringFunctions.slotToStartPeriod(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean checkTeacherDailyLimit(Long teacherId, int dayOfWeek, int maxSlots, List<SchedulePlanItem> generatedItems) {
        if (maxSlots <= 0) {
            return true;
        }
        long count = generatedItems.stream()
                .filter(item -> Objects.equals(item.getTeacherId(), teacherId))
                .filter(item -> Objects.equals(item.getWeekday(), dayOfWeek))
                .count();
        return count < maxSlots;
    }

    private boolean checkClassDailyLimit(Long classId, int dayOfWeek, int maxSlots, List<SchedulePlanItem> generatedItems) {
        if (maxSlots <= 0) {
            return true;
        }
        long count = generatedItems.stream()
                .filter(item -> Objects.equals(item.getClassId(), classId))
                .filter(item -> Objects.equals(item.getWeekday(), dayOfWeek))
                .count();
        return count < maxSlots;
    }

    private boolean hasSameCourseSameDay(Long classId, Long courseId, int dayOfWeek, List<SchedulePlanItem> generatedItems) {
        return generatedItems.stream()
                .anyMatch(item ->
                        Objects.equals(item.getClassId(), classId)
                                && Objects.equals(item.getCourseId(), courseId)
                                && Objects.equals(item.getWeekday(), dayOfWeek));
    }

    /**
     * 贪心循环里的<b>在线评分</b>：给单个 (slot, room) 候选打"越大越好"的正向分。
     * 跟 {@link ScheduleScoreService} 里的<b>离线 rescore</b> 用的是<b>不同公式</b>（双轨制），
     * 每个维度的在线/离线公式差异参见
     * {@link com.paike.scheduler.service.scheduling.ScoringDimensions#ONLINE_SOFT} 与
     * {@link com.paike.scheduler.service.scheduling.ScoringDimensions#OFFLINE_SOFT}。
     * 在线分仅用于贪心选谁，不持久化；用户看到的 totalScore（如 90.13）来自离线 rescore。
     */
    private double scoreCandidate(
            TeachingTask task,
            TimeSlot slot,
            Classroom room,
            List<SchedulePlanItem> generatedItems,
            SchedulingReferenceData refData
    ) {
        if (USE_DELTA_PENALTY_SCORING) {
            return scoreCandidateDeltaPenalty(task, slot, room, generatedItems, refData);
        }
        return scoreCandidateLegacy(task, slot, room, generatedItems, refData);
    }

    private double scoreCandidateLegacy(
            TeachingTask task,
            TimeSlot slot,
            Classroom room,
            List<SchedulePlanItem> generatedItems,
            SchedulingReferenceData refData
    ) {
        double score = 0D;
        String courseType = SchedulingSupport.getCourseType(task.getCourseId(), refData.courseMap());
        int studentCount = SchedulingSupport.getClassStudentCount(task.getClassId(), refData.classMap());

        score += weight(refData,"CLASSROOM_UTILIZATION") * ScoringFunctions.candidateClassroomUtilization(room, studentCount);
        score += weight(refData,"CLASS_DAILY_BALANCE") * ScoringFunctions.candidateBalance(generatedItems, item -> Objects.equals(item.getClassId(), task.getClassId()), slot.getDayOfWeek());
        score += weight(refData,"TEACHER_DAILY_LOAD") * ScoringFunctions.candidateBalance(generatedItems, item -> Objects.equals(item.getTeacherId(), task.getTeacherId()), slot.getDayOfWeek());
        score += weight(refData,"COURSE_DISTRIBUTION") * ScoringFunctions.candidateCourseDistribution(generatedItems, task, slot.getDayOfWeek());
        score += weight(refData,"CONTINUOUS_PERIOD_LIMIT") * ScoringFunctions.candidateContinuousLimit(generatedItems, task, slot);
        score += weight(refData,"MORNING_THEORY_PRIORITY") * ScoringFunctions.candidateMorningPriority(courseType, slot);

        // 稳定偏好：更早的时间段略优，避免在候选分相同时结果抖动。
        score += stableTieBreaker(slot);
        return score;
    }

    private double scoreCandidateDeltaPenalty(
            TeachingTask task,
            TimeSlot slot,
            Classroom room,
            List<SchedulePlanItem> generatedItems,
            SchedulingReferenceData refData
    ) {
        SchedulePlanItem candidate = toPlanItem(null, task, slot, room);
        BigDecimal weightedDeltaPenalty = DeltaPenaltyScorer.weightedSoftDeltaPenalty(
                refData.weightMap(),
                generatedItems,
                candidate,
                thresholdProperties.getAfternoonStartPeriod()
        );
        return weightedDeltaPenalty.negate().doubleValue() + stableTieBreaker(slot);
    }

    private double stableTieBreaker(TimeSlot slot) {
        return Math.max(0, 100 - slot.getSortOrder()) * 0.0001D;
    }

    private double weight(SchedulingReferenceData refData, String ruleCode) {
        return refData.weightMap().getOrDefault(ruleCode, BigDecimal.ZERO).doubleValue();
    }

    private SchedulePlanItem toPlanItem(Long planId, TeachingTask task, TimeSlot slot, Classroom room) {
        SchedulePlanItem item = new SchedulePlanItem();
        item.setPlanId(planId);
        item.setSemesterId(task.getSemesterId());
        item.setTeachingTaskId(task.getId());
        item.setTeacherId(task.getTeacherId());
        item.setClassId(task.getClassId());
        item.setCourseId(task.getCourseId());
        item.setClassroomId(room.getId());
        item.setWeekday(slot.getDayOfWeek());
        item.setStartPeriod(ScoringFunctions.slotToStartPeriod(slot));
        item.setEndPeriod(slotToEndPeriod(slot));
        item.setWeekType("ALL");
        item.setScore(null);
        item.setConflictFlag(0);
        item.setConflictReason(null);
        item.setSourceType("AUTO");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }

    private int slotToEndPeriod(TimeSlot slot) {
        return ScoringFunctions.slotToStartPeriod(slot) + 1;
    }

    private ScheduleGenerateResult toResult(SchedulePlan plan) {
        ScheduleGenerateResult result = new ScheduleGenerateResult();
        result.setPlanId(plan.getId());
        result.setPlanName(plan.getName());
        result.setStrategyType(plan.getStrategyType());
        result.setTotalScore(plan.getTotalScore());
        result.setScheduledCount(plan.getScheduledCount());
        result.setUnscheduledCount(plan.getUnscheduledCount());
        result.setConflictCount(plan.getConflictCount());
        return result;
    }

    private record Candidate(TimeSlot slot, Classroom room, double score) {
    }

    private record UnassignedReason(String reasonCode, String reasonMessage, String suggestion) {
    }

    private static final class StepCounter {
        private int value;

        private StepCounter() {
            this(0);
        }

        private StepCounter(int start) {
            this.value = start;
        }

        private int next() {
            value += 1;
            return value;
        }
    }

    private UnassignedReason buildNoMatchedRoomReason(TeachingTask task, int studentCount, String courseType, List<Classroom> allRooms) {
        boolean hasCourseTypeRoom = allRooms.stream().anyMatch(room -> SchedulingSupport.isRoomTypeMatched(courseType, room.getRoomType()));
        if (!hasCourseTypeRoom) {
            return new UnassignedReason(
                    "CLASSROOM_TYPE_MISMATCH",
                    "当前学期没有满足课程类型要求的教室资源",
                    "请补充匹配类型教室，或调整课程的教室类型要求");
        }
        return new UnassignedReason(
                "CLASSROOM_CAPACITY_NOT_ENOUGH",
                "满足课程类型的教室容量不足，班级人数为 " + studentCount,
                "请调整到更大容量教室，或拆分教学班");
    }

    private UnassignedReason analyzeUnassignedReason(
            TeachingTask task,
            Set<Integer> usedDays,
            List<Classroom> matchedRooms,
            List<SchedulePlanItem> generatedItems,
            SchedulingReferenceData refData,
            RuleConfig rules
    ) {
        boolean teacherUnavailable = false;
        boolean teacherConflict = false;
        boolean classConflict = false;
        boolean roomConflict = false;
        boolean classDayLimited = false;

        for (TimeSlot slot : refData.sortedTimeSlots()) {
            if (refData.unavailableKeySet().contains(task.getTeacherId() + "_" + slot.getId())) {
                teacherUnavailable = true;
                continue;
            }
            if (!checkTeacherDailyLimit(task.getTeacherId(), slot.getDayOfWeek(), rules.teacherMaxDailySlots(), generatedItems)) {
                teacherConflict = true;
                continue;
            }
            if (!checkClassDailyLimit(task.getClassId(), slot.getDayOfWeek(), rules.classMaxDailySlots(), generatedItems)) {
                classDayLimited = true;
                continue;
            }
            if (!rules.allowSameCourseSameDay() && usedDays.contains(slot.getDayOfWeek())) {
                classConflict = true;
                continue;
            }
            if (!rules.allowSameCourseSameDay() && hasSameCourseSameDay(task.getClassId(), task.getCourseId(), slot.getDayOfWeek(), generatedItems)) {
                classConflict = true;
                continue;
            }

            boolean anyRoomAvailable = false;
            for (Classroom room : matchedRooms) {
                if (!hasConflict(task, slot, room, generatedItems)) {
                    anyRoomAvailable = true;
                    break;
                }
                roomConflict = true;
                teacherConflict = teacherConflict || generatedItems.stream().anyMatch(item ->
                        Objects.equals(item.getTeacherId(), task.getTeacherId())
                                && Objects.equals(item.getWeekday(), slot.getDayOfWeek())
                                && Objects.equals(item.getStartPeriod(), ScoringFunctions.slotToStartPeriod(slot)));
                classConflict = classConflict || generatedItems.stream().anyMatch(item ->
                        Objects.equals(item.getClassId(), task.getClassId())
                                && Objects.equals(item.getWeekday(), slot.getDayOfWeek())
                                && Objects.equals(item.getStartPeriod(), ScoringFunctions.slotToStartPeriod(slot)));
            }
            if (anyRoomAvailable) {
                return new UnassignedReason(
                        "UNKNOWN_REASON",
                        "存在候选位置但未成功写入，请检查排课过程日志",
                        "查看该教学任务的生成日志，确认具体筛选步骤");
            }
        }

        if (teacherUnavailable) {
            return new UnassignedReason(
                    "TEACHER_UNAVAILABLE",
                    "教师可用时间被禁排规则完全覆盖",
                    "请减少教师禁排时间，或调整任课教师");
        }
        if (teacherConflict) {
            return new UnassignedReason(
                    "TEACHER_TIME_CONFLICT",
                    "教师候选时间均被已有排课占用或超过日上限",
                    "请调整教师已有课程，或放宽教师每日排课限制");
        }
        if (classConflict || classDayLimited) {
            return new UnassignedReason(
                    "CLASS_TIME_CONFLICT",
                    "班级候选时间均被已有课程占用，或超过班级日排课限制",
                    "请调整班级已有课表，或放宽班级每日排课限制");
        }
        if (roomConflict) {
            return new UnassignedReason(
                    "NO_AVAILABLE_CLASSROOM",
                    "候选时间段内没有空闲教室可供安排",
                    "请释放教室资源，或增加同类型教室");
        }
        return new UnassignedReason(
                "PERIOD_NOT_ENOUGH",
                "当前可用节次不足以安排该教学任务",
                "请新增时间段，或降低同课程同日/日排课限制");
    }

    private String taskLabel(TeachingTask task, Map<Long, Course> courseMap, Map<Long, ClassInfo> classMap) {
        Course course = courseMap.get(task.getCourseId());
        ClassInfo classInfo = classMap.get(task.getClassId());
        String courseName = course != null ? course.getCourseName() : "未知课程";
        String className = classInfo != null ? classInfo.getClassName() : "未知班级";
        return courseName + "-" + className;
    }
}
