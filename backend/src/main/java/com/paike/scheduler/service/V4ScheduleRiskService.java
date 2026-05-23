package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleUnassignedTask;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.vo.ScheduleRiskIssueVo;
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class V4ScheduleRiskService {

    private final SchedulePlanMapper schedulePlanMapper;
    private final SchedulePlanItemMapper schedulePlanItemMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TeacherUnavailableTimeService teacherUnavailableTimeService;
    private final SchedulePlanExplainService schedulePlanExplainService;
    private final ScheduleThresholdProperties thresholds;

    public ScheduleRiskListVo getPlanRisks(Long planId, String riskType, String level, Boolean onlyUnresolved) {
        SchedulePlan plan = schedulePlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }

        List<SchedulePlanItem> items = schedulePlanItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, planId)
                        .orderByAsc(SchedulePlanItem::getWeekday)
                        .orderByAsc(SchedulePlanItem::getStartPeriod));
        List<ScheduleUnassignedTask> unassignedTasks = schedulePlanExplainService.listUnassignedTasks(planId);

        RiskContext context = buildContext(items);
        AtomicLong idGenerator = new AtomicLong(1);
        List<ScheduleRiskIssueVo> risks = new ArrayList<>();
        detectTeacherConflicts(context, idGenerator, risks);
        detectClassConflicts(context, idGenerator, risks);
        detectRoomConflicts(context, idGenerator, risks);
        detectTeacherUnavailable(context, idGenerator, risks);
        detectRoomCapacity(context, idGenerator, risks);
        detectRoomType(context, idGenerator, risks);
        detectUnscheduledTasks(unassignedTasks, context, idGenerator, risks);
        detectTeacherOverload(context, idGenerator, risks);
        detectClassDailyOverload(context, idGenerator, risks);
        detectRoomUtilization(context, idGenerator, risks);

        List<ScheduleRiskIssueVo> filteredRisks = risks.stream()
                .filter(risk -> riskType == null || riskType.isBlank() || riskType.equalsIgnoreCase(risk.getRiskType()))
                .filter(risk -> level == null || level.isBlank() || level.equalsIgnoreCase(risk.getLevel()))
                .filter(risk -> onlyUnresolved == null || !onlyUnresolved || !Boolean.TRUE.equals(risk.getResolved()))
                .sorted(Comparator
                        .comparingInt(this::riskLevelOrder)
                        .thenComparing(ScheduleRiskIssueVo::getRiskType)
                        .thenComparing(ScheduleRiskIssueVo::getTitle))
                .toList();

        ScheduleRiskListVo vo = new ScheduleRiskListVo();
        vo.setPlanId(planId);
        vo.setRiskCount(filteredRisks.size());
        vo.setHighRiskCount((int) filteredRisks.stream().filter(risk -> "HIGH".equals(risk.getLevel())).count());
        vo.setMediumRiskCount((int) filteredRisks.stream().filter(risk -> "MEDIUM".equals(risk.getLevel())).count());
        vo.setLowRiskCount((int) filteredRisks.stream().filter(risk -> "LOW".equals(risk.getLevel())).count());
        vo.setUnresolvedCount((int) filteredRisks.stream().filter(risk -> !Boolean.TRUE.equals(risk.getResolved())).count());
        vo.setRisks(filteredRisks);
        return vo;
    }

    private RiskContext buildContext(List<SchedulePlanItem> items) {
        RiskContext context = new RiskContext();
        context.items = items;
        context.teacherMap = loadByIds(teacherMapper, collectIds(items, SchedulePlanItem::getTeacherId), Teacher::getId);
        context.classMap = loadByIds(classInfoMapper, collectIds(items, SchedulePlanItem::getClassId), ClassInfo::getId);
        context.roomMap = loadByIds(classroomMapper, collectIds(items, SchedulePlanItem::getClassroomId), Classroom::getId);
        context.courseMap = loadByIds(courseMapper, collectIds(items, SchedulePlanItem::getCourseId), Course::getId);
        context.taskMap = loadByIds(teachingTaskMapper, collectIds(items, SchedulePlanItem::getTeachingTaskId), TeachingTask::getId);
        context.slotMap = timeSlotMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(slot -> slot.getDayOfWeek() + "_" + slot.getPeriodNo(), Function.identity(), (a, b) -> a));
        context.totalTimeSlots = Math.max(timeSlotMapper.selectCount(new LambdaQueryWrapper<TimeSlot>()), 1L);
        return context;
    }

    private <T> Map<Long, T> loadByIds(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, List<Long> ids, Function<T, Long> keyExtractor) {
        if (ids.isEmpty()) return Map.of();
        return mapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(keyExtractor, Function.identity(), (a, b) -> a));
    }

    private <T> List<Long> collectIds(List<SchedulePlanItem> items, Function<SchedulePlanItem, Long> idFunc) {
        return items.stream()
                .map(idFunc)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void detectTeacherConflicts(RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        detectSlotConflicts(context, idGenerator, risks, "TEACHER_CONFLICT", "教师时间冲突",
                SchedulePlanItem::getTeacherId, context.teacherMap, Teacher::getName,
                "同一时间被安排了多门课程，建议调整其中一门课程到其他空闲时间段。");
    }

    private void detectClassConflicts(RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        detectSlotConflicts(context, idGenerator, risks, "CLASS_CONFLICT", "班级时间冲突",
                SchedulePlanItem::getClassId, context.classMap, ClassInfo::getClassName,
                "同一班级在同一时间被安排了多门课程，建议重新分配时间段。");
    }

    private void detectRoomConflicts(RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        detectSlotConflicts(context, idGenerator, risks, "ROOM_CONFLICT", "教室时间冲突",
                SchedulePlanItem::getClassroomId, context.roomMap, Classroom::getRoomName,
                "同一教室在同一时间存在重复占用，建议调整教室或时间段。");
    }

    private <T> void detectSlotConflicts(
            RiskContext context,
            AtomicLong idGenerator,
            List<ScheduleRiskIssueVo> risks,
            String riskType,
            String riskTypeName,
            Function<SchedulePlanItem, Long> ownerIdFunc,
            Map<Long, T> ownerMap,
            Function<T, String> ownerNameFunc,
            String suggestion
    ) {
        Map<String, List<SchedulePlanItem>> grouped = context.items.stream()
                .filter(item -> ownerIdFunc.apply(item) != null)
                .collect(Collectors.groupingBy(item -> ownerIdFunc.apply(item) + "_" + item.getWeekday() + "_" + item.getStartPeriod()));

        for (List<SchedulePlanItem> sameSlotItems : grouped.values()) {
            if (sameSlotItems.size() <= 1) {
                continue;
            }
            SchedulePlanItem first = sameSlotItems.get(0);
            Long ownerId = ownerIdFunc.apply(first);
            T owner = ownerMap.get(ownerId);
            String ownerName = owner == null ? "未知对象" : safeName(ownerNameFunc.apply(owner));
            List<String> courses = sameSlotItems.stream().map(item -> buildItemCourseLabel(item, context)).distinct().toList();

            ScheduleRiskIssueVo risk = baseRisk(idGenerator, riskType, riskTypeName, "HIGH");
            risk.setTitle(ownerName + " " + formatWeekDay(first.getWeekday()) + " " + formatPeriod(first) + " 存在时间冲突");
            risk.setDescription(ownerName + " 在同一时间被安排了 " + sameSlotItems.size() + " 门课程：" + String.join("、", courses));
            risk.setWeekDay(first.getWeekday());
            risk.setPeriod(formatPeriod(first));
            risk.setSuggestion(suggestion);
            risk.setAffectedObjects(ownerName);
            risk.setRelatedItemIds(sameSlotItems.stream().map(SchedulePlanItem::getId).toList());
            risk.setDetailLines(buildConflictDetailLines(context, sameSlotItems));
            fillRelationsFromFirst(risk, first, context);
            if ("TEACHER_CONFLICT".equals(riskType)) {
                risk.setRelatedTeacherId(ownerId);
                risk.setRelatedTeacherName(ownerName);
            } else if ("CLASS_CONFLICT".equals(riskType)) {
                risk.setRelatedClassId(ownerId);
                risk.setRelatedClassName(ownerName);
            } else if ("ROOM_CONFLICT".equals(riskType)) {
                risk.setRelatedRoomId(ownerId);
                risk.setRelatedRoomName(ownerName);
            }
            risks.add(risk);
        }
    }

    private void detectTeacherUnavailable(RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        for (SchedulePlanItem item : context.items) {
            TimeSlot slot = resolveTimeSlot(item, context);
            Long teacherId = item.getTeacherId();
            if (slot == null || teacherId == null || !teacherUnavailableTimeService.isUnavailable(teacherId, slot.getId())) {
                continue;
            }
            Teacher teacher = context.teacherMap.get(teacherId);
            ScheduleRiskIssueVo risk = baseRisk(idGenerator, "TEACHER_UNAVAILABLE", "教师禁排时间冲突", "HIGH");
            risk.setTitle(safeName(teacher == null ? null : teacher.getName()) + " 在禁排时间被安排课程");
            risk.setDescription("该课程命中了教师禁排时间，当前时间段为 " + safeName(slot.getTimeLabel()) + "。");
            risk.setWeekDay(item.getWeekday());
            risk.setPeriod(formatPeriod(item));
            risk.setSuggestion("建议调整到教师允许授课的其他时间段。");
            risk.setAffectedObjects(buildItemSummary(item, context));
            risk.setRelatedItemIds(List.of(item.getId()));
            risk.setDetailLines(List.of(
                    "教师：" + safeName(teacher == null ? null : teacher.getName()),
                    "课程：" + safeName(item.getCourseName()),
                    "班级：" + safeName(item.getClassName()),
                    "时间：" + safeName(slot.getTimeLabel())
            ));
            fillRelationsFromFirst(risk, item, context);
            risks.add(risk);
        }
    }

    private void detectRoomCapacity(RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        for (SchedulePlanItem item : context.items) {
            ClassInfo classInfo = context.classMap.get(item.getClassId());
            Classroom room = context.roomMap.get(item.getClassroomId());
            if (classInfo == null || room == null || classInfo.getStudentCount() == null || room.getCapacity() == null
                    || classInfo.getStudentCount() <= room.getCapacity()) {
                continue;
            }
            ScheduleRiskIssueVo risk = baseRisk(idGenerator, "ROOM_CAPACITY", "教室容量不足", "HIGH");
            risk.setTitle(safeName(room.getRoomName()) + " 容量不足，无法容纳 " + safeName(classInfo.getClassName()));
            risk.setDescription("班级人数 " + classInfo.getStudentCount() + "，教室容量 " + room.getCapacity() + "。");
            risk.setWeekDay(item.getWeekday());
            risk.setPeriod(formatPeriod(item));
            risk.setSuggestion("建议更换容量更大的教室，或拆分教学任务。");
            risk.setAffectedObjects(buildItemSummary(item, context));
            risk.setRelatedItemIds(List.of(item.getId()));
            risk.setDetailLines(List.of(
                    "班级：" + safeName(classInfo.getClassName()) + "（" + classInfo.getStudentCount() + " 人）",
                    "教室：" + safeName(room.getRoomName()) + "（容量 " + room.getCapacity() + "）",
                    "课程：" + safeName(item.getCourseName())
            ));
            fillRelationsFromFirst(risk, item, context);
            risks.add(risk);
        }
    }

    private void detectRoomType(RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        for (SchedulePlanItem item : context.items) {
            Course course = context.courseMap.get(item.getCourseId());
            Classroom room = context.roomMap.get(item.getClassroomId());
            if (!isRoomTypeMismatch(course, room)) {
                continue;
            }
            ScheduleRiskIssueVo risk = baseRisk(idGenerator, "ROOM_TYPE", "教室类型不匹配", "HIGH");
            risk.setTitle(safeName(course == null ? null : course.getCourseName()) + " 与 " + safeName(room == null ? null : room.getRoomName()) + " 类型不匹配");
            risk.setDescription(buildRoomTypeMismatchDescription(course, room));
            risk.setWeekDay(item.getWeekday());
            risk.setPeriod(formatPeriod(item));
            risk.setSuggestion("建议更换符合课程类型要求的教室。");
            risk.setAffectedObjects(buildItemSummary(item, context));
            risk.setRelatedItemIds(List.of(item.getId()));
            risk.setDetailLines(List.of(
                    "课程类型：" + courseTypeText(course == null ? null : course.getCourseType()),
                    "教室类型：" + roomTypeText(room == null ? null : room.getRoomType()),
                    "课程：" + safeName(course == null ? null : course.getCourseName())
            ));
            fillRelationsFromFirst(risk, item, context);
            risks.add(risk);
        }
    }

    private void detectUnscheduledTasks(List<ScheduleUnassignedTask> unassignedTasks, RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        for (ScheduleUnassignedTask task : unassignedTasks) {
            ScheduleRiskIssueVo risk = baseRisk(idGenerator, "UNSCHEDULED_TASK", "教学任务未排", "HIGH");
            risk.setTitle(safeName(task.getClassName()) + " 的 " + safeName(task.getCourseName()) + " 仍未排入课表");
            risk.setDescription(safeName(task.getReasonMessage()));
            risk.setSuggestion(task.getSuggestion() == null || task.getSuggestion().isBlank()
                    ? "建议优先处理未排任务，再继续分析其他优化项。"
                    : task.getSuggestion());
            risk.setAffectedObjects(String.join(" / ", List.of(
                    safeName(task.getCourseName()),
                    safeName(task.getTeacherName()),
                    safeName(task.getClassName())
            )));
            risk.setDetailLines(List.of(
                    "课程：" + safeName(task.getCourseName()),
                    "教师：" + safeName(task.getTeacherName()),
                    "班级：" + safeName(task.getClassName()),
                    "原因：" + safeName(task.getReasonMessage())
            ));
            TeachingTask teachingTask = context.taskMap.get(task.getTeachingTaskId());
            if (teachingTask != null) {
                risk.setRelatedTeacherId(teachingTask.getTeacherId());
                risk.setRelatedClassId(teachingTask.getClassId());
                risk.setRelatedCourseId(teachingTask.getCourseId());
                risk.setRelatedTeacherName(task.getTeacherName());
                risk.setRelatedClassName(task.getClassName());
                risk.setRelatedCourseName(task.getCourseName());
            }
            risks.add(risk);
        }
    }

    private void detectTeacherOverload(RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        Map<Long, Integer> teacherLoads = new LinkedHashMap<>();
        for (SchedulePlanItem item : context.items) {
            teacherLoads.merge(item.getTeacherId(), lessonPeriods(item), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : teacherLoads.entrySet()) {
            if (entry.getKey() == null || entry.getValue() < thresholds.getTeacherOverloadMedium()) {
                continue;
            }
            Teacher teacher = context.teacherMap.get(entry.getKey());
            String level = entry.getValue() >= thresholds.getTeacherOverloadHigh() ? "HIGH" : "MEDIUM";
            ScheduleRiskIssueVo risk = baseRisk(idGenerator, "TEACHER_OVERLOAD", "教师课时过高", level);
            risk.setTitle(safeName(teacher == null ? null : teacher.getName()) + " 当前总课时偏高");
            risk.setDescription("当前方案中，该教师累计课时为 " + entry.getValue() + " 节。");
            risk.setSuggestion("建议在后续局部调整阶段平衡教师负载。");
            risk.setAffectedObjects(safeName(teacher == null ? null : teacher.getName()));
            risk.setRelatedTeacherId(entry.getKey());
            risk.setRelatedTeacherName(teacher == null ? null : teacher.getName());
            risk.setDetailLines(buildTeacherLoadLines(context, entry.getKey(), entry.getValue()));
            risks.add(risk);
        }
    }

    private void detectClassDailyOverload(RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        Map<String, Integer> classDailyLoads = new LinkedHashMap<>();
        Map<String, SchedulePlanItem> firstItemMap = new HashMap<>();
        for (SchedulePlanItem item : context.items) {
            String key = item.getClassId() + "_" + item.getWeekday();
            classDailyLoads.merge(key, lessonPeriods(item), Integer::sum);
            firstItemMap.putIfAbsent(key, item);
        }
        for (Map.Entry<String, Integer> entry : classDailyLoads.entrySet()) {
            if (entry.getValue() < thresholds.getClassDailyOverloadMedium()) {
                continue;
            }
            SchedulePlanItem first = firstItemMap.get(entry.getKey());
            ClassInfo classInfo = first == null ? null : context.classMap.get(first.getClassId());
            String level = entry.getValue() >= thresholds.getClassDailyOverloadHigh() ? "HIGH" : "MEDIUM";
            ScheduleRiskIssueVo risk = baseRisk(idGenerator, "CLASS_DAILY_OVERLOAD", "班级单日课程过多", level);
            risk.setTitle(safeName(classInfo == null ? null : classInfo.getClassName()) + " 在 " + formatWeekDay(first == null ? null : first.getWeekday()) + " 课时偏高");
            risk.setDescription("该班级当天累计安排 " + entry.getValue() + " 节课程。");
            risk.setWeekDay(first == null ? null : first.getWeekday());
            risk.setSuggestion("建议将部分课程分散到其他天，避免班级负载集中。");
            risk.setAffectedObjects(safeName(classInfo == null ? null : classInfo.getClassName()));
            risk.setRelatedClassId(classInfo == null ? null : classInfo.getId());
            risk.setRelatedClassName(classInfo == null ? null : classInfo.getClassName());
            risk.setDetailLines(buildClassDailyLoadLines(context, first == null ? null : first.getClassId(), first == null ? null : first.getWeekday(), entry.getValue()));
            risks.add(risk);
        }
    }

    private void detectRoomUtilization(RiskContext context, AtomicLong idGenerator, List<ScheduleRiskIssueVo> risks) {
        Map<Long, Integer> roomLoads = new LinkedHashMap<>();
        for (SchedulePlanItem item : context.items) {
            roomLoads.merge(item.getClassroomId(), lessonPeriods(item), Integer::sum);
        }
        BigDecimal denominator = BigDecimal.valueOf(context.totalTimeSlots * 2);
        for (Map.Entry<Long, Integer> entry : roomLoads.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            Classroom room = context.roomMap.get(entry.getKey());
            BigDecimal rate = BigDecimal.valueOf(entry.getValue())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(denominator, 1, RoundingMode.HALF_UP);
            if (rate.compareTo(thresholds.getRoomLowUtilization()) < 0) {
                ScheduleRiskIssueVo risk = baseRisk(idGenerator, "ROOM_LOW_UTILIZATION", "教室利用率偏低", "LOW");
                risk.setTitle(safeName(room == null ? null : room.getRoomName()) + " 利用率偏低");
                risk.setDescription("当前方案中，该教室利用率为 " + rate.stripTrailingZeros().toPlainString() + "%。");
                risk.setSuggestion("建议在图表分析阶段继续观察低利用率教室的分布。");
                risk.setAffectedObjects(safeName(room == null ? null : room.getRoomName()));
                risk.setRelatedRoomId(entry.getKey());
                risk.setRelatedRoomName(room == null ? null : room.getRoomName());
                risk.setDetailLines(List.of(
                        "教室：" + safeName(room == null ? null : room.getRoomName()),
                        "累计课时：" + entry.getValue() + " 节",
                        "利用率：" + rate.stripTrailingZeros().toPlainString() + "%"
                ));
                risks.add(risk);
            } else if (rate.compareTo(thresholds.getRoomHighUtilization()) >= 0) {
                ScheduleRiskIssueVo risk = baseRisk(idGenerator, "ROOM_HIGH_UTILIZATION", "教室利用率偏高", "MEDIUM");
                risk.setTitle(safeName(room == null ? null : room.getRoomName()) + " 利用率偏高");
                risk.setDescription("当前方案中，该教室利用率为 " + rate.stripTrailingZeros().toPlainString() + "%。");
                risk.setSuggestion("建议在后续调整阶段关注该教室是否成为瓶颈资源。");
                risk.setAffectedObjects(safeName(room == null ? null : room.getRoomName()));
                risk.setRelatedRoomId(entry.getKey());
                risk.setRelatedRoomName(room == null ? null : room.getRoomName());
                risk.setDetailLines(List.of(
                        "教室：" + safeName(room == null ? null : room.getRoomName()),
                        "累计课时：" + entry.getValue() + " 节",
                        "利用率：" + rate.stripTrailingZeros().toPlainString() + "%"
                ));
                risks.add(risk);
            }
        }
    }

    private ScheduleRiskIssueVo baseRisk(AtomicLong idGenerator, String riskType, String riskTypeName, String level) {
        ScheduleRiskIssueVo risk = new ScheduleRiskIssueVo();
        risk.setId(idGenerator.getAndIncrement());
        risk.setRiskType(riskType);
        risk.setRiskTypeName(riskTypeName);
        risk.setLevel(level);
        risk.setResolved(false);
        return risk;
    }

    private void fillRelationsFromFirst(ScheduleRiskIssueVo risk, SchedulePlanItem item, RiskContext context) {
        Teacher teacher = context.teacherMap.get(item.getTeacherId());
        ClassInfo classInfo = context.classMap.get(item.getClassId());
        Classroom room = context.roomMap.get(item.getClassroomId());
        Course course = context.courseMap.get(item.getCourseId());
        risk.setRelatedTeacherId(item.getTeacherId());
        risk.setRelatedTeacherName(teacher == null ? item.getTeacherName() : teacher.getName());
        risk.setRelatedClassId(item.getClassId());
        risk.setRelatedClassName(classInfo == null ? item.getClassName() : classInfo.getClassName());
        risk.setRelatedRoomId(item.getClassroomId());
        risk.setRelatedRoomName(room == null ? item.getRoomName() : room.getRoomName());
        risk.setRelatedCourseId(item.getCourseId());
        risk.setRelatedCourseName(course == null ? item.getCourseName() : course.getCourseName());
    }

    private List<String> buildConflictDetailLines(RiskContext context, List<SchedulePlanItem> items) {
        return items.stream()
                .map(item -> buildItemSummary(item, context) + " / " + formatWeekDay(item.getWeekday()) + " " + formatPeriod(item))
                .distinct()
                .toList();
    }

    private List<String> buildTeacherLoadLines(RiskContext context, Long teacherId, int totalLoad) {
        List<String> dailyLoads = context.items.stream()
                .filter(item -> Objects.equals(item.getTeacherId(), teacherId))
                .collect(Collectors.groupingBy(SchedulePlanItem::getWeekday, LinkedHashMap::new, Collectors.summingInt(this::lessonPeriods)))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> formatWeekDay(entry.getKey()) + "：" + entry.getValue() + " 节")
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add("总课时：" + totalLoad + " 节");
        lines.addAll(dailyLoads);
        return lines;
    }

    private List<String> buildClassDailyLoadLines(RiskContext context, Long classId, Integer weekDay, int totalLoad) {
        List<String> courses = context.items.stream()
                .filter(item -> Objects.equals(item.getClassId(), classId) && Objects.equals(item.getWeekday(), weekDay))
                .sorted(Comparator.comparing(SchedulePlanItem::getStartPeriod))
                .map(item -> formatPeriod(item) + " " + safeName(item.getCourseName()) + " / " + safeName(item.getTeacherName()))
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add("当日总课时：" + totalLoad + " 节");
        lines.addAll(courses);
        return lines;
    }

    private boolean isRoomTypeMismatch(Course course, Classroom room) {
        if (course == null || room == null) {
            return false;
        }
        if (CourseType.EXPERIMENT.getCode().equals(course.getCourseType())) {
            return !RoomType.LAB.getCode().equals(room.getRoomType());
        }
        if (CourseType.COMPUTER.getCode().equals(course.getCourseType())) {
            return !RoomType.COMPUTER.getCode().equals(room.getRoomType());
        }
        return false;
    }

    private String buildRoomTypeMismatchDescription(Course course, Classroom room) {
        if (course == null || room == null) {
            return "课程类型与教室类型不匹配。";
        }
        return safeName(course.getCourseName()) + " 为 " + courseTypeText(course.getCourseType())
                + "，但当前安排在 " + safeName(room.getRoomName()) + "，教室类型为 " + roomTypeText(room.getRoomType()) + "。";
    }

    private String buildItemSummary(SchedulePlanItem item, RiskContext context) {
        Course course = context.courseMap.get(item.getCourseId());
        Teacher teacher = context.teacherMap.get(item.getTeacherId());
        ClassInfo classInfo = context.classMap.get(item.getClassId());
        return String.join(" / ", List.of(
                safeName(course == null ? item.getCourseName() : course.getCourseName()),
                safeName(teacher == null ? item.getTeacherName() : teacher.getName()),
                safeName(classInfo == null ? item.getClassName() : classInfo.getClassName())
        ));
    }

    private String buildItemCourseLabel(SchedulePlanItem item, RiskContext context) {
        Course course = context.courseMap.get(item.getCourseId());
        ClassInfo classInfo = context.classMap.get(item.getClassId());
        return safeName(course == null ? item.getCourseName() : course.getCourseName())
                + " / "
                + safeName(classInfo == null ? item.getClassName() : classInfo.getClassName());
    }

    private TimeSlot resolveTimeSlot(SchedulePlanItem item, RiskContext context) {
        if (item.getWeekday() == null || item.getStartPeriod() == null || item.getEndPeriod() == null) {
            return null;
        }
        if (item.getStartPeriod() % 2 == 0 || item.getEndPeriod() - item.getStartPeriod() != 1) {
            return null;
        }
        int periodNo = (item.getStartPeriod() + 1) / 2;
        return context.slotMap.get(item.getWeekday() + "_" + periodNo);
    }

    private int lessonPeriods(SchedulePlanItem item) {
        if (item.getStartPeriod() == null || item.getEndPeriod() == null) {
            return 0;
        }
        return Math.max(item.getEndPeriod() - item.getStartPeriod() + 1, 0);
    }

    private int riskLevelOrder(ScheduleRiskIssueVo risk) {
        if ("HIGH".equals(risk.getLevel())) {
            return 0;
        }
        if ("MEDIUM".equals(risk.getLevel())) {
            return 1;
        }
        return 2;
    }

    private String formatWeekDay(Integer weekDay) {
        if (weekDay == null) {
            return "未知日期";
        }
        return "周" + weekDay;
    }

    private String formatPeriod(SchedulePlanItem item) {
        if (item == null || item.getStartPeriod() == null || item.getEndPeriod() == null) {
            return "未知节次";
        }
        return item.getStartPeriod() + "-" + item.getEndPeriod() + " 节";
    }

    private String safeName(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private String courseTypeText(String courseType) {
        CourseType type = CourseType.fromCode(courseType);
        return type == null ? safeName(courseType) : type.getLabel();
    }

    private String roomTypeText(String roomType) {
        RoomType type = RoomType.fromCode(roomType);
        return type == null ? safeName(roomType) : type.getLabel();
    }

    private static class RiskContext {
        private List<SchedulePlanItem> items = List.of();
        private Map<Long, Teacher> teacherMap = Map.of();
        private Map<Long, ClassInfo> classMap = Map.of();
        private Map<Long, Classroom> roomMap = Map.of();
        private Map<Long, Course> courseMap = Map.of();
        private Map<Long, TeachingTask> taskMap = Map.of();
        private Map<String, TimeSlot> slotMap = Map.of();
        private long totalTimeSlots;
    }
}
