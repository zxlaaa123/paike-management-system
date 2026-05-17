package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.dto.SchedulePlanItemAdjustRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchedulePlanService {

    private final SchedulePlanMapper planMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TeacherUnavailableTimeService unavailableTimeService;
    private final ScheduleScoreService scoreService;
    private final SchedulePlanExplainService explainService;

    public Page<SchedulePlan> list(Long semesterId, String status, String strategyType, String keyword, int page, int size) {
        LambdaQueryWrapper<SchedulePlan> wrapper = new LambdaQueryWrapper<SchedulePlan>()
                .eq(SchedulePlan::getSemesterId, semesterId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SchedulePlan::getStatus, status);
        }
        if (strategyType != null && !strategyType.isBlank()) {
            wrapper.eq(SchedulePlan::getStrategyType, strategyType);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SchedulePlan::getName, keyword);
        }
        wrapper.orderByDesc(SchedulePlan::getCreatedAt);
        return planMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public SchedulePlan getById(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        return plan;
    }

    public List<SchedulePlanItem> getPlanItems(Long planId) {
        List<SchedulePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, planId)
                        .orderByAsc(SchedulePlanItem::getWeekday)
                        .orderByAsc(SchedulePlanItem::getStartPeriod));
        fillItemRelations(items);
        return items;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if (!"DRAFT".equals(plan.getStatus())) {
            throw new BusinessException("只能删除草稿方案");
        }
        planItemMapper.delete(new LambdaQueryWrapper<SchedulePlanItem>().eq(SchedulePlanItem::getPlanId, id));
        explainService.clearPlanArtifacts(id);
        planMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void abandon(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        plan.setStatus("ABANDONED");
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> adjustPlanItem(Long itemId, SchedulePlanItemAdjustRequest request) {
        SchedulePlanItem item = planItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("方案明细不存在");
        }
        SchedulePlan plan = planMapper.selectById(item.getPlanId());
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if ("ABANDONED".equals(plan.getStatus())) {
            throw new BusinessException("已废弃方案不能调整");
        }
        BigDecimal beforeScore = normalizeScore(plan.getTotalScore());

        Classroom classroom = classroomMapper.selectById(request.getClassroomId());
        if (classroom == null || classroom.getDeleted() == 1) {
            throw new BusinessException("所选教室不存在");
        }
        if (classroom.getStatus() == null || classroom.getStatus() != 1) {
            throw new BusinessException("所选教室已停用，不能调整");
        }

        TimeSlot timeSlot = resolveTimeSlot(request.getWeekday(), request.getStartPeriod(), request.getEndPeriod());
        if (timeSlot == null) {
            throw new BusinessException("所选时间段不存在");
        }

        SchedulePlanItem before = copyItem(item);
        if (Objects.equals(before.getClassroomId(), request.getClassroomId())
                && Objects.equals(before.getWeekday(), request.getWeekday())
                && Objects.equals(before.getStartPeriod(), request.getStartPeriod())
                && Objects.equals(before.getEndPeriod(), request.getEndPeriod())) {
            throw new BusinessException("调整后的方案明细与原记录一致");
        }

        item.setClassroomId(request.getClassroomId());
        item.setWeekday(request.getWeekday());
        item.setStartPeriod(request.getStartPeriod());
        item.setEndPeriod(request.getEndPeriod());
        item.setSourceType("MANUAL");
        item.setUpdatedAt(LocalDateTime.now());
        planItemMapper.updateById(item);

        refreshPlanConflictState(plan.getId());
        scoreService.rescore(planMapper.selectById(plan.getId()));

        SchedulePlan refreshedPlan = planMapper.selectById(plan.getId());
        SchedulePlanItem refreshedItem = planItemMapper.selectById(itemId);

        boolean syncFormalSchedule = "APPLIED".equals(refreshedPlan.getStatus());
        Long scheduleId = null;
        if (syncFormalSchedule) {
            scheduleId = syncAppliedSchedule(refreshedPlan, before, refreshedItem, timeSlot.getId());
        }

        ScheduleAdjustLog log = new ScheduleAdjustLog();
        log.setPlanId(refreshedPlan.getId());
        log.setScheduleId(scheduleId);
        log.setSemesterId(refreshedPlan.getSemesterId());
        log.setTeachingTaskId(refreshedItem.getTeachingTaskId());
        log.setOldClassroomId(before.getClassroomId());
        log.setOldWeekday(before.getWeekday());
        log.setOldStartPeriod(before.getStartPeriod());
        log.setOldEndPeriod(before.getEndPeriod());
        log.setNewClassroomId(refreshedItem.getClassroomId());
        log.setNewWeekday(refreshedItem.getWeekday());
        log.setNewStartPeriod(refreshedItem.getStartPeriod());
        log.setNewEndPeriod(refreshedItem.getEndPeriod());
        log.setBeforeScore(beforeScore);
        log.setAfterScore(normalizeScore(refreshedPlan.getTotalScore()));
        log.setConflictFlag(refreshedItem.getConflictFlag());
        log.setAdjustReason(request.getAdjustReason().trim());
        explainService.appendAdjustLog(log);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemId", refreshedItem.getId());
        result.put("planId", refreshedPlan.getId());
        result.put("beforeScore", log.getBeforeScore());
        result.put("afterScore", log.getAfterScore());
        result.put("conflictFlag", refreshedItem.getConflictFlag() == null ? 0 : refreshedItem.getConflictFlag());
        result.put("conflictReason", refreshedItem.getConflictReason());
        result.put("syncFormalSchedule", syncFormalSchedule);
        result.put("scheduleId", scheduleId);
        result.put("message", syncFormalSchedule ? "已同步正式课表" : "仅更新方案草稿");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public int refreshPlanConflictState(Long planId) {
        List<SchedulePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, planId)
                        .orderByAsc(SchedulePlanItem::getWeekday)
                        .orderByAsc(SchedulePlanItem::getStartPeriod));
        if (items.isEmpty()) {
            return 0;
        }

        Map<Long, TeachingTask> taskMap = teachingTaskMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getTeachingTaskId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(TeachingTask::getId, Function.identity(), (a, b) -> a));
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(taskMap.values().stream()
                        .map(TeachingTask::getCourseId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherMapper.selectBatchIds(taskMap.values().stream()
                        .map(TeachingTask::getTeacherId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        Map<Long, ClassInfo> classMap = classInfoMapper.selectBatchIds(taskMap.values().stream()
                        .map(TeachingTask::getClassId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));
        Map<Long, Classroom> roomMap = classroomMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getClassroomId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));
        Map<String, TimeSlot> slotMap = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()).stream()
                .collect(Collectors.toMap(slot -> slot.getDayOfWeek() + "_" + slot.getPeriodNo(), Function.identity(), (a, b) -> a));

        int conflictCount = 0;
        for (SchedulePlanItem item : items) {
            List<String> reasons = buildConflictReasons(item, items, taskMap, courseMap, teacherMap, classMap, roomMap, slotMap);
            item.setConflictFlag(reasons.isEmpty() ? 0 : 1);
            item.setConflictReason(reasons.isEmpty() ? null : String.join("；", reasons));
            item.setUpdatedAt(LocalDateTime.now());
            planItemMapper.updateById(item);
            if (!reasons.isEmpty()) {
                conflictCount++;
            }
        }

        SchedulePlan plan = planMapper.selectById(planId);
        if (plan != null) {
            plan.setConflictCount(conflictCount);
            plan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(plan);
        }
        return conflictCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyPlan(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if ("ABANDONED".equals(plan.getStatus())) {
            throw new BusinessException("已废弃方案不能应用");
        }
        if (plan.getScheduledCount() == null || plan.getScheduledCount() == 0) {
            throw new BusinessException("该方案没有排课明细，无法应用");
        }

        Long semesterId = plan.getSemesterId();

        List<SchedulePlan> oldAppliedPlans = planMapper.selectList(
                new LambdaQueryWrapper<SchedulePlan>()
                        .eq(SchedulePlan::getSemesterId, semesterId)
                        .eq(SchedulePlan::getStatus, "APPLIED"));
        for (SchedulePlan oldPlan : oldAppliedPlans) {
            scheduleMapper.update(null,
                    new LambdaUpdateWrapper<Schedule>()
                            .eq(Schedule::getSemesterId, semesterId)
                            .eq(Schedule::getPlanId, oldPlan.getId())
                            .set(Schedule::getDeleted, 1)
                            .set(Schedule::getUpdateTime, LocalDateTime.now()));
            oldPlan.setStatus("DRAFT");
            oldPlan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(oldPlan);
        }

        List<SchedulePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, id));

        Map<String, Long> timeSlotMap = timeSlotMapper.selectList(null).stream()
                .collect(Collectors.toMap(
                        ts -> ts.getDayOfWeek() + "_" + ts.getPeriodNo(),
                        TimeSlot::getId,
                        (a, b) -> a));

        int insertedCount = 0;
        for (SchedulePlanItem item : items) {
            int periodNo = (item.getStartPeriod() + 1) / 2;
            String key = item.getWeekday() + "_" + periodNo;
            Long timeSlotId = timeSlotMap.get(key);
            if (timeSlotId == null) {
                throw new BusinessException("无法找到对应的时间段：周" + item.getWeekday() + " 第" + item.getStartPeriod() + "-" + item.getEndPeriod() + "节");
            }

            Schedule schedule = new Schedule();
            schedule.setSemesterId(semesterId);
            schedule.setPlanId(plan.getId());
            schedule.setTeachingTaskId(item.getTeachingTaskId());
            schedule.setCourseId(item.getCourseId());
            schedule.setTeacherId(item.getTeacherId());
            schedule.setClassId(item.getClassId());
            schedule.setClassroomId(item.getClassroomId());
            schedule.setTimeSlotId(timeSlotId);
            schedule.setSourceType("PLAN");
            schedule.setDeleted(0);
            schedule.setCreateTime(LocalDateTime.now());
            schedule.setUpdateTime(LocalDateTime.now());
            scheduleMapper.insert(schedule);
            insertedCount++;
        }

        plan.setStatus("APPLIED");
        plan.setAppliedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", plan.getId());
        result.put("semesterId", semesterId);
        result.put("appliedCount", insertedCount);
        result.put("appliedAt", plan.getAppliedAt());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rollbackPlan(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if ("ABANDONED".equals(plan.getStatus())) {
            throw new BusinessException("已废弃方案不能回滚应用");
        }
        if (!"APPLIED".equals(plan.getStatus())) {
            throw new BusinessException("只有已应用的方案才能回滚");
        }

        Long semesterId = plan.getSemesterId();

        // 1. 软删除该方案生成的所有正式课表记录
        int deletedCount = scheduleMapper.update(null,
                new LambdaUpdateWrapper<Schedule>()
                        .eq(Schedule::getSemesterId, semesterId)
                        .eq(Schedule::getPlanId, id)
                        .set(Schedule::getDeleted, 1)
                        .set(Schedule::getUpdateTime, LocalDateTime.now()));

        // 2. 将方案状态回退到 DRAFT
        plan.setStatus("DRAFT");
        plan.setAppliedAt(null);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", plan.getId());
        result.put("semesterId", semesterId);
        result.put("rolledBackScheduleCount", deletedCount);
        result.put("message", "已回滚，共清除 " + deletedCount + " 条正式课表记录");
        return result;
    }

    private void fillItemRelations(List<SchedulePlanItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<Long, Course> courseMap = courseMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getCourseId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getTeacherId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        Map<Long, ClassInfo> classMap = classInfoMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getClassId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));
        Map<Long, Classroom> roomMap = classroomMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getClassroomId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));

        for (SchedulePlanItem item : items) {
            Course course = courseMap.get(item.getCourseId());
            Teacher teacher = teacherMap.get(item.getTeacherId());
            ClassInfo classInfo = classMap.get(item.getClassId());
            Classroom room = roomMap.get(item.getClassroomId());
            item.setCourseName(course != null ? course.getCourseName() : null);
            item.setTeacherName(teacher != null ? teacher.getName() : null);
            item.setClassName(classInfo != null ? classInfo.getClassName() : null);
            item.setRoomName(room != null ? room.getRoomName() : null);
            item.setTimeLabel("周" + item.getWeekday() + " 第" + item.getStartPeriod() + "-" + item.getEndPeriod() + "节");
        }
    }

    private List<String> buildConflictReasons(
            SchedulePlanItem item,
            List<SchedulePlanItem> items,
            Map<Long, TeachingTask> taskMap,
            Map<Long, Course> courseMap,
            Map<Long, Teacher> teacherMap,
            Map<Long, ClassInfo> classMap,
            Map<Long, Classroom> roomMap,
            Map<String, TimeSlot> slotMap
    ) {
        List<String> reasons = new ArrayList<>();
        TeachingTask task = taskMap.get(item.getTeachingTaskId());
        Teacher teacher = teacherMap.get(item.getTeacherId());
        ClassInfo classInfo = classMap.get(item.getClassId());
        Classroom room = roomMap.get(item.getClassroomId());
        Course course = courseMap.get(item.getCourseId());
        TimeSlot slot = slotMap.get(item.getWeekday() + "_" + ((item.getStartPeriod() + 1) / 2));

        if (task != null && slot != null && unavailableTimeService.isUnavailable(task.getTeacherId(), slot.getId())) {
            reasons.add("教师禁排时间冲突");
        }
        if (classInfo != null && room != null && classInfo.getStudentCount() != null && room.getCapacity() != null
                && classInfo.getStudentCount() > room.getCapacity()) {
            reasons.add("教室容量不足");
        }
        if (course != null && room != null && CourseType.EXPERIMENT.getCode().equals(course.getCourseType())
                && !RoomType.LAB.getCode().equals(room.getRoomType())) {
            reasons.add("教室类型不匹配");
        }
        if (course != null && room != null && CourseType.COMPUTER.getCode().equals(course.getCourseType())
                && !RoomType.COMPUTER.getCode().equals(room.getRoomType())) {
            reasons.add("教室类型不匹配");
        }

        for (SchedulePlanItem other : items) {
            if (Objects.equals(other.getId(), item.getId())) {
                continue;
            }
            if (!Objects.equals(other.getWeekday(), item.getWeekday()) || !Objects.equals(other.getStartPeriod(), item.getStartPeriod())) {
                continue;
            }
            if (Objects.equals(other.getTeacherId(), item.getTeacherId())) {
                reasons.add("教师时间冲突：" + safeName(teacher != null ? teacher.getName() : null));
            }
            if (Objects.equals(other.getClassId(), item.getClassId())) {
                reasons.add("班级时间冲突：" + safeName(classInfo != null ? classInfo.getClassName() : null));
            }
            if (Objects.equals(other.getClassroomId(), item.getClassroomId())) {
                reasons.add("教室时间冲突：" + safeName(room != null ? room.getRoomName() : null));
            }
        }

        return reasons.stream().distinct().collect(Collectors.toList());
    }

    private TimeSlot resolveTimeSlot(Integer weekday, Integer startPeriod, Integer endPeriod) {
        if (weekday == null || startPeriod == null || endPeriod == null) {
            return null;
        }
        if (startPeriod % 2 == 0 || endPeriod - startPeriod != 1) {
            return null;
        }
        int periodNo = (startPeriod + 1) / 2;
        return timeSlotMapper.selectOne(new LambdaQueryWrapper<TimeSlot>()
                .eq(TimeSlot::getDayOfWeek, weekday)
                .eq(TimeSlot::getPeriodNo, periodNo));
    }

    private SchedulePlanItem copyItem(SchedulePlanItem item) {
        SchedulePlanItem copy = new SchedulePlanItem();
        copy.setId(item.getId());
        copy.setPlanId(item.getPlanId());
        copy.setSemesterId(item.getSemesterId());
        copy.setTeachingTaskId(item.getTeachingTaskId());
        copy.setTeacherId(item.getTeacherId());
        copy.setClassId(item.getClassId());
        copy.setCourseId(item.getCourseId());
        copy.setClassroomId(item.getClassroomId());
        copy.setWeekday(item.getWeekday());
        copy.setStartPeriod(item.getStartPeriod());
        copy.setEndPeriod(item.getEndPeriod());
        copy.setWeekType(item.getWeekType());
        copy.setScore(item.getScore());
        copy.setConflictFlag(item.getConflictFlag());
        copy.setConflictReason(item.getConflictReason());
        copy.setSourceType(item.getSourceType());
        copy.setCreatedAt(item.getCreatedAt());
        copy.setUpdatedAt(item.getUpdatedAt());
        return copy;
    }

    private Long syncAppliedSchedule(SchedulePlan plan, SchedulePlanItem before, SchedulePlanItem after, Long newTimeSlotId) {
        TimeSlot oldSlot = resolveTimeSlot(before.getWeekday(), before.getStartPeriod(), before.getEndPeriod());
        if (oldSlot == null) {
            throw new BusinessException("无法定位调整前的正式课表时间段");
        }
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getPlanId, plan.getId())
                .eq(Schedule::getTeachingTaskId, before.getTeachingTaskId())
                .eq(Schedule::getTimeSlotId, oldSlot.getId())
                .eq(Schedule::getDeleted, 0));
        if (schedules.isEmpty()) {
            throw new BusinessException("已应用方案缺少对应正式课表记录，无法同步");
        }
        Schedule schedule = schedules.get(0);
        schedule.setClassroomId(after.getClassroomId());
        schedule.setTimeSlotId(newTimeSlotId);
        schedule.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(schedule);
        return schedule.getId();
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            return null;
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private String safeName(String value) {
        return value == null ? "未知" : value;
    }
}
