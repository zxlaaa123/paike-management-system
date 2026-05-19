package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.ScheduleLockedItem;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRuleWeightMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.dto.V5CandidateEvaluateRequest;
import com.paike.scheduler.service.vo.V5CandidateEvaluationVo;
import com.paike.scheduler.service.vo.V5RuleCheckDetailVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class V5RuleEvaluationService {

    private final SchedulePlanMapper schedulePlanMapper;
    private final SchedulePlanItemMapper schedulePlanItemMapper;
    private final ScheduleLockedItemMapper scheduleLockedItemMapper;
    private final ScheduleRuleWeightMapper scheduleRuleWeightMapper;
    private final TeacherUnavailableTimeService unavailableTimeService;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TimeSlotMapper timeSlotMapper;

    public V5CandidateEvaluationVo evaluateCandidate(V5CandidateEvaluateRequest request) {
        SchedulePlan plan = requirePlan(request.getPlanId());
        SchedulePlanItem item = requirePlanItem(plan.getId(), request.getPlanItemId());
        Classroom room = requireRoom(request.getCandidateClassroomId());
        Teacher teacher = teacherMapper.selectById(item.getTeacherId());
        ClassInfo classInfo = classInfoMapper.selectById(item.getClassId());
        Course course = courseMapper.selectById(item.getCourseId());
        List<SchedulePlanItem> allItems = schedulePlanItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, plan.getId()));

        Map<String, BigDecimal> weights = loadWeights(plan.getSemesterId(), plan.getStrategyType());
        List<V5RuleCheckDetailVo> details = new ArrayList<>();

        checkRepairRules(request, plan, item, details);
        checkHardRules(request, item, room, teacher, classInfo, course, allItems, details);
        checkSoftRules(request, item, room, allItems, weights, details);
        checkPreferenceRules(request, item, room, course, allItems, weights, details);

        int hardViolationCount = (int) details.stream().filter(d -> "HARD".equals(d.getRuleType()) && Boolean.FALSE.equals(d.getPassed())).count();
        int repairViolationCount = (int) details.stream().filter(d -> "REPAIR".equals(d.getRuleType()) && Boolean.FALSE.equals(d.getPassed())).count();
        boolean available = hardViolationCount == 0 && repairViolationCount == 0;

        BigDecimal softScore = sumType(details, "SOFT");
        BigDecimal prefScore = sumType(details, "PREFERENCE");
        BigDecimal total = softScore.add(prefScore).setScale(2, RoundingMode.HALF_UP);

        V5CandidateEvaluationVo vo = new V5CandidateEvaluationVo();
        vo.setPlanId(plan.getId());
        vo.setPlanItemId(item.getId());
        vo.setCandidateWeekday(request.getCandidateWeekday());
        vo.setCandidateStartPeriod(request.getCandidateStartPeriod());
        vo.setCandidateEndPeriod(request.getCandidateEndPeriod());
        vo.setCandidateClassroomId(request.getCandidateClassroomId());
        vo.setAvailable(available);
        vo.setHardViolationCount(hardViolationCount + repairViolationCount);
        vo.setSoftScoreDelta(softScore);
        vo.setPreferenceScoreDelta(prefScore);
        vo.setTotalScoreDelta(total);
        vo.setSummary(buildSummary(available, details));
        vo.setDetails(details);
        return vo;
    }

    private void checkRepairRules(V5CandidateEvaluateRequest request, SchedulePlan plan, SchedulePlanItem item, List<V5RuleCheckDetailVo> details) {
        boolean locked = isLocked(plan.getId(), item.getId());
        details.add(repair("LOCKED_ITEM_IMMUTABLE", "锁定课程不可移动", !locked, locked ? "当前课程已锁定，禁止移动" : "课程未锁定，可评估"));

        Set<Long> scopeSet = request.getScopePlanItemIds() == null ? Set.of() : request.getScopePlanItemIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        boolean inScope = scopeSet.isEmpty() || scopeSet.contains(item.getId());
        details.add(repair("REPAIR_SCOPE_LIMIT", "只修复指定范围", inScope, inScope ? "当前课程在修复范围内" : "当前课程不在修复范围内"));

        boolean simulationOnly = !Boolean.FALSE.equals(request.getSimulationOnly());
        details.add(repair("SIMULATION_ONLY", "只生成试算方案", simulationOnly, simulationOnly ? "当前为试算模式" : "请求未启用试算模式"));

        boolean noDirectFormalOverwrite = !simulationOnly || !"APPLIED".equalsIgnoreCase(plan.getStatus()) ? simulationOnly : false;
        details.add(repair("NO_DIRECT_FORMAL_OVERWRITE", "不直接覆盖正式课表",
                noDirectFormalOverwrite,
                noDirectFormalOverwrite ? "当前评估不会直接覆盖正式课表" : "已应用方案必须仅输出试算结果"));
    }

    private void checkHardRules(
            V5CandidateEvaluateRequest request,
            SchedulePlanItem item,
            Classroom room,
            Teacher teacher,
            ClassInfo classInfo,
            Course course,
            List<SchedulePlanItem> allItems,
            List<V5RuleCheckDetailVo> details
    ) {
        List<SchedulePlanItem> overlaps = allItems.stream()
                .filter(other -> !Objects.equals(other.getId(), item.getId()))
                .filter(other -> Objects.equals(other.getWeekday(), request.getCandidateWeekday()))
                .filter(other -> overlap(request.getCandidateStartPeriod(), request.getCandidateEndPeriod(), other.getStartPeriod(), other.getEndPeriod()))
                .toList();

        boolean teacherConflict = overlaps.stream().anyMatch(other -> Objects.equals(other.getTeacherId(), item.getTeacherId()));
        details.add(hard("TEACHER_TIME_CONFLICT", "教师时间冲突", !teacherConflict,
                teacherConflict ? safeName(teacher == null ? null : teacher.getName()) + " 在该时段已有课程" : "教师时段可用"));

        boolean classConflict = overlaps.stream().anyMatch(other -> Objects.equals(other.getClassId(), item.getClassId()));
        details.add(hard("CLASS_TIME_CONFLICT", "班级时间冲突", !classConflict,
                classConflict ? safeName(classInfo == null ? null : classInfo.getClassName()) + " 在该时段已有课程" : "班级时段可用"));

        boolean roomConflict = overlaps.stream().anyMatch(other -> Objects.equals(other.getClassroomId(), request.getCandidateClassroomId()));
        details.add(hard("CLASSROOM_TIME_CONFLICT", "教室时间冲突", !roomConflict,
                roomConflict ? safeName(room.getRoomName()) + " 在该时段已被占用" : "教室时段可用"));

        Long slotId = resolveSlotId(request.getCandidateWeekday(), request.getCandidateStartPeriod(), request.getCandidateEndPeriod());
        boolean unavailable = slotId != null && unavailableTimeService.isUnavailable(item.getTeacherId(), slotId);
        details.add(hard("TEACHER_UNAVAILABLE", "教师禁排", !unavailable,
                unavailable ? safeName(teacher == null ? null : teacher.getName()) + " 命中禁排时段" : "未命中禁排时段"));

        boolean capacityViolation = classInfo != null && classInfo.getStudentCount() != null && room.getCapacity() != null && classInfo.getStudentCount() > room.getCapacity();
        details.add(hard("CLASSROOM_CAPACITY", "教室容量", !capacityViolation,
                capacityViolation ? "班级人数超过教室容量" : "容量满足"));

        boolean typeMismatch = isRoomTypeMismatch(course, room);
        details.add(hard("CLASSROOM_TYPE_MISMATCH", "教室类型", !typeMismatch,
                typeMismatch ? "课程类型与教室类型不匹配" : "教室类型匹配"));
    }

    private void checkSoftRules(
            V5CandidateEvaluateRequest request,
            SchedulePlanItem item,
            Classroom room,
            List<SchedulePlanItem> allItems,
            Map<String, BigDecimal> weights,
            List<V5RuleCheckDetailVo> details
    ) {
        long teacherDayLoad = allItems.stream()
                .filter(other -> !Objects.equals(other.getId(), item.getId()))
                .filter(other -> Objects.equals(other.getTeacherId(), item.getTeacherId()))
                .filter(other -> Objects.equals(other.getWeekday(), request.getCandidateWeekday()))
                .count();
        details.add(soft("TEACHER_DAILY_LOAD", "教师日负载", weights,
                boundedPenalty(teacherDayLoad, 3, 8),
                "教师当日已有 " + teacherDayLoad + " 个大节"));

        long classDayLoad = allItems.stream()
                .filter(other -> !Objects.equals(other.getId(), item.getId()))
                .filter(other -> Objects.equals(other.getClassId(), item.getClassId()))
                .filter(other -> Objects.equals(other.getWeekday(), request.getCandidateWeekday()))
                .count();
        details.add(soft("CLASS_DAILY_BALANCE", "班级日负载", weights,
                boundedPenalty(classDayLoad, 4, 9),
                "班级当日已有 " + classDayLoad + " 个大节"));

        long sameCourseDay = allItems.stream()
                .filter(other -> !Objects.equals(other.getId(), item.getId()))
                .filter(other -> Objects.equals(other.getClassId(), item.getClassId()))
                .filter(other -> Objects.equals(other.getCourseId(), item.getCourseId()))
                .filter(other -> Objects.equals(other.getWeekday(), request.getCandidateWeekday()))
                .count();
        details.add(soft("COURSE_DISTRIBUTION", "课程分布", weights,
                sameCourseDay > 0 ? BigDecimal.valueOf(0.6) : BigDecimal.ZERO,
                sameCourseDay > 0 ? "同课程同日已有安排，分布偏集中" : "课程分布正常"));

        long consecutiveAround = allItems.stream()
                .filter(other -> !Objects.equals(other.getId(), item.getId()))
                .filter(other -> Objects.equals(other.getTeacherId(), item.getTeacherId()))
                .filter(other -> Objects.equals(other.getWeekday(), request.getCandidateWeekday()))
                .filter(other -> nearConsecutive(request.getCandidateStartPeriod(), other.getStartPeriod()))
                .count();
        details.add(soft("CONTINUOUS_PERIOD_LIMIT", "连续上课", weights,
                boundedPenalty(consecutiveAround, 1, 3),
                "教师相邻连排课程数 " + consecutiveAround));

        long roomUsage = allItems.stream().filter(other -> Objects.equals(other.getClassroomId(), room.getId())).count();
        long total = Math.max(allItems.size(), 1);
        BigDecimal usagePenalty = BigDecimal.ONE.subtract(BigDecimal.valueOf(roomUsage).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP));
        details.add(soft("CLASSROOM_UTILIZATION", "教室利用率", weights,
                usagePenalty.max(BigDecimal.ZERO).min(BigDecimal.ONE),
                "教室历史使用占比 " + roomUsage + "/" + total));

        BigDecimal timeDistributionPenalty = request.getCandidateStartPeriod() >= 7 ? BigDecimal.valueOf(0.5) : BigDecimal.valueOf(0.1);
        details.add(soft("TIME_DISTRIBUTION", "时间段分布", weights, timeDistributionPenalty,
                request.getCandidateStartPeriod() >= 7 ? "时段较晚，分布质量较低" : "时段分布较优"));
    }

    private void checkPreferenceRules(
            V5CandidateEvaluateRequest request,
            SchedulePlanItem item,
            Classroom room,
            Course course,
            List<SchedulePlanItem> allItems,
            Map<String, BigDecimal> weights,
            List<V5RuleCheckDetailVo> details
    ) {
        boolean morning = request.getCandidateStartPeriod() <= 4;
        BigDecimal morningReward = morning ? BigDecimal.valueOf(-0.2) : BigDecimal.valueOf(0.3);
        details.add(preference("MORNING_THEORY_PRIORITY", "上午优先", weights, morningReward,
                morning ? "上午时段符合偏好" : "下午/晚间时段不符合上午优先"));

        boolean experimentInLab = course != null && CourseType.EXPERIMENT.getCode().equals(course.getCourseType()) && RoomType.LAB.getCode().equals(room.getRoomType());
        BigDecimal labReward = course != null && CourseType.EXPERIMENT.getCode().equals(course.getCourseType())
                ? (experimentInLab ? BigDecimal.valueOf(-0.4) : BigDecimal.valueOf(0.4))
                : BigDecimal.ZERO;
        details.add(preference("EXPERIMENT_LAB_PREFERENCE", "实验课优先实验室", weights, labReward,
                experimentInLab ? "实验课安排在实验室，符合偏好" : "非实验课或未命中实验室偏好"));

        boolean peNight = course != null && CourseType.PE.getCode().equals(course.getCourseType()) && request.getCandidateStartPeriod() >= 7;
        details.add(preference("PE_AVOID_NIGHT", "体育课避免晚间", weights,
                peNight ? BigDecimal.valueOf(0.6) : BigDecimal.valueOf(-0.1),
                peNight ? "体育课安排在晚间，偏好较差" : "未命中体育课晚间风险"));

        int targetDay = request.getCandidateWeekday() == null ? -1 : request.getCandidateWeekday();
        long teacherDayCount = allItems.stream()
                .filter(other -> !Objects.equals(other.getId(), item.getId()))
                .filter(other -> Objects.equals(other.getTeacherId(), item.getTeacherId()))
                .filter(other -> Objects.equals(other.getWeekday(), targetDay))
                .count();
        details.add(preference("TEACHER_SLOT_PREFERENCE", "教师偏好时间段", weights,
                teacherDayCount <= 2 ? BigDecimal.valueOf(-0.2) : BigDecimal.valueOf(0.2),
                teacherDayCount <= 2 ? "教师当天负载适中，符合偏好" : "教师当天负载偏高，偏好较弱"));
    }

    private SchedulePlan requirePlan(Long planId) {
        SchedulePlan plan = schedulePlanMapper.selectById(planId);
        if (plan == null) throw new BusinessException("排课方案不存在");
        return plan;
    }

    private SchedulePlanItem requirePlanItem(Long planId, Long itemId) {
        SchedulePlanItem item = schedulePlanItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getPlanId(), planId)) {
            throw new BusinessException("方案明细不存在或不属于当前方案");
        }
        return item;
    }

    private Classroom requireRoom(Long roomId) {
        Classroom room = classroomMapper.selectById(roomId);
        if (room == null || Integer.valueOf(1).equals(room.getDeleted())) {
            throw new BusinessException("候选教室不存在");
        }
        return room;
    }

    private boolean isLocked(Long planId, Long planItemId) {
        return scheduleLockedItemMapper.selectCount(new LambdaQueryWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getPlanId, planId)
                .eq(ScheduleLockedItem::getPlanItemId, planItemId)
                .eq(ScheduleLockedItem::getActiveFlag, 1)) > 0;
    }

    private Map<String, BigDecimal> loadWeights(Long semesterId, String strategyType) {
        Map<String, BigDecimal> weights = new HashMap<>();
        List<ScheduleRuleWeight> list = scheduleRuleWeightMapper.selectList(new LambdaQueryWrapper<ScheduleRuleWeight>()
                .eq(ScheduleRuleWeight::getSemesterId, semesterId)
                .eq(ScheduleRuleWeight::getStrategyType, strategyType));
        for (ScheduleRuleWeight rule : list) {
            if (rule.getWeight() != null && (rule.getEnabled() == null || rule.getEnabled() == 1)) {
                weights.put(rule.getRuleCode(), rule.getWeight());
            }
        }
        return weights;
    }

    private V5RuleCheckDetailVo hard(String code, String name, boolean passed, String message) {
        V5RuleCheckDetailVo vo = new V5RuleCheckDetailVo();
        vo.setRuleCode(code);
        vo.setRuleName(name);
        vo.setRuleType("HARD");
        vo.setPassed(passed);
        vo.setBlocking(!passed);
        vo.setScoreDelta(BigDecimal.ZERO);
        vo.setMessage(message);
        return vo;
    }

    private V5RuleCheckDetailVo repair(String code, String name, boolean passed, String message) {
        V5RuleCheckDetailVo vo = new V5RuleCheckDetailVo();
        vo.setRuleCode(code);
        vo.setRuleName(name);
        vo.setRuleType("REPAIR");
        vo.setPassed(passed);
        vo.setBlocking(!passed);
        vo.setScoreDelta(BigDecimal.ZERO);
        vo.setMessage(message);
        return vo;
    }

    private V5RuleCheckDetailVo soft(String code, String name, Map<String, BigDecimal> weights, BigDecimal penaltyFactor, String message) {
        BigDecimal weight = weights.getOrDefault(code, BigDecimal.valueOf(10));
        BigDecimal score = weight.multiply(penaltyFactor.max(BigDecimal.ZERO).min(BigDecimal.ONE)).negate().setScale(2, RoundingMode.HALF_UP);
        V5RuleCheckDetailVo vo = new V5RuleCheckDetailVo();
        vo.setRuleCode(code);
        vo.setRuleName(name);
        vo.setRuleType("SOFT");
        vo.setPassed(true);
        vo.setBlocking(false);
        vo.setScoreDelta(score);
        vo.setMessage(message + "，软约束得分变化 " + score);
        return vo;
    }

    private V5RuleCheckDetailVo preference(String code, String name, Map<String, BigDecimal> weights, BigDecimal factor, String message) {
        BigDecimal weight = weights.getOrDefault(code, BigDecimal.valueOf(8));
        BigDecimal score = weight.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        V5RuleCheckDetailVo vo = new V5RuleCheckDetailVo();
        vo.setRuleCode(code);
        vo.setRuleName(name);
        vo.setRuleType("PREFERENCE");
        vo.setPassed(true);
        vo.setBlocking(false);
        vo.setScoreDelta(score.negate());
        vo.setMessage(message + "，偏好得分变化 " + score.negate());
        return vo;
    }

    private BigDecimal sumType(List<V5RuleCheckDetailVo> details, String type) {
        return details.stream()
                .filter(d -> type.equals(d.getRuleType()))
                .map(V5RuleCheckDetailVo::getScoreDelta)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean overlap(Integer aStart, Integer aEnd, Integer bStart, Integer bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) return false;
        return !(aEnd < bStart || bEnd < aStart);
    }

    private boolean nearConsecutive(Integer aStart, Integer bStart) {
        if (aStart == null || bStart == null) return false;
        return Math.abs(aStart - bStart) == 2;
    }

    private boolean isRoomTypeMismatch(Course course, Classroom room) {
        if (course == null || room == null) return false;
        if (CourseType.EXPERIMENT.getCode().equals(course.getCourseType())) {
            return !RoomType.LAB.getCode().equals(room.getRoomType());
        }
        if (CourseType.COMPUTER.getCode().equals(course.getCourseType())) {
            return !RoomType.COMPUTER.getCode().equals(room.getRoomType());
        }
        return false;
    }

    private BigDecimal boundedPenalty(long value, long goodThreshold, long badThreshold) {
        if (value <= goodThreshold) return BigDecimal.ZERO;
        if (value >= badThreshold) return BigDecimal.ONE;
        BigDecimal numerator = BigDecimal.valueOf(value - goodThreshold);
        BigDecimal denominator = BigDecimal.valueOf(badThreshold - goodThreshold);
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private String buildSummary(boolean available, List<V5RuleCheckDetailVo> details) {
        long hardFail = details.stream().filter(d -> ("HARD".equals(d.getRuleType()) || "REPAIR".equals(d.getRuleType())) && Boolean.FALSE.equals(d.getPassed())).count();
        BigDecimal total = details.stream()
                .map(V5RuleCheckDetailVo::getScoreDelta)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!available) {
            return "候选位置不可用：存在 " + hardFail + " 条阻塞约束。";
        }
        return "候选位置可用：综合评分变化 " + total.setScale(2, RoundingMode.HALF_UP).toPlainString() + "。";
    }

    private Long resolveSlotId(Integer weekday, Integer startPeriod, Integer endPeriod) {
        if (weekday == null || startPeriod == null || endPeriod == null || startPeriod % 2 == 0 || endPeriod - startPeriod != 1) return null;
        int periodNo = (startPeriod + 1) / 2;
        TimeSlot slot = timeSlotMapper.selectOne(new LambdaQueryWrapper<TimeSlot>()
                .eq(TimeSlot::getDayOfWeek, weekday)
                .eq(TimeSlot::getPeriodNo, periodNo));
        return slot == null ? null : slot.getId();
    }

    private String safeName(String value) {
        if (value == null || value.isBlank()) return "未知";
        return value.trim();
    }
}
