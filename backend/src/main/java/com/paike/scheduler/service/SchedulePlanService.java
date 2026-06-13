package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.common.enums.SchedulePlanStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.dto.SchedulePlanItemAdjustRequest;
import com.paike.scheduler.service.vo.AdjustPlanResultVo;
import com.paike.scheduler.service.vo.ApplyPlanResultVo;
import com.paike.scheduler.service.vo.SchedulePlanItemVo;
import com.paike.scheduler.service.vo.SchedulePlanVo;
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
    private final SemesterMapper semesterMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleLockedItemMapper scheduleLockedItemMapper;
    private final ScheduleLockGuardService lockGuardService;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TeacherUnavailableTimeService unavailableTimeService;
    private final ScheduleScoreService scoreService;
    private final SchedulePlanExplainService explainService;
    private final SystemAuditLogService auditLogService;

    public Page<SchedulePlan> list(Long semesterId, String status, String strategyType, String keyword, int page, int size) {
        LambdaQueryWrapper<SchedulePlan> wrapper = new LambdaQueryWrapper<SchedulePlan>()
                .eq(SchedulePlan::getSemesterId, semesterId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SchedulePlan::getStatus, status);
        } else {
            wrapper.notIn(SchedulePlan::getStatus, List.of(SchedulePlanStatus.SIMULATION.getCode(), SchedulePlanStatus.CONFIRMED.getCode(), SchedulePlanStatus.DISCARDED.getCode()));
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

    public Page<SchedulePlanVo> listVo(Long semesterId, String status, String strategyType, String keyword, int page, int size) {
        Page<SchedulePlan> source = list(semesterId, status, strategyType, keyword, page, size);
        List<SchedulePlanVo> records = source.getRecords().stream()
                .map(SchedulePlanVo::fromEntity)
                .collect(Collectors.toList());
        fillPlanDisplayFields(records);

        Page<SchedulePlanVo> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(records);
        return result;
    }

    public SchedulePlan getById(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        return plan;
    }

    public SchedulePlanVo getVoById(Long id) {
        SchedulePlanVo vo = SchedulePlanVo.fromEntity(getById(id));
        fillPlanDisplayFields(List.of(vo));
        return vo;
    }

    public List<SchedulePlanItemVo> getPlanItems(Long planId) {
        List<SchedulePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, planId)
                        .orderByAsc(SchedulePlanItem::getWeekday)
                        .orderByAsc(SchedulePlanItem::getStartPeriod));
        List<SchedulePlanItemVo> vos = items.stream().map(this::planItemToVo).collect(Collectors.toList());
        fillItemRelations(vos);
        return vos;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if (!SchedulePlanStatus.DRAFT.is(plan.getStatus())) {
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
        plan.setStatus(SchedulePlanStatus.ABANDONED.getCode());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdjustPlanResultVo adjustPlanItem(Long itemId, SchedulePlanItemAdjustRequest request) {
        if (request.getAdjustReason() == null || request.getAdjustReason().trim().isEmpty()) {
            throw new BusinessException("调整原因不能为空");
        }
        String adjustReason = request.getAdjustReason().trim();

        SchedulePlanItem item = planItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("方案明细不存在");
        }
        SchedulePlan plan = planMapper.selectById(item.getPlanId());
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if (SchedulePlanStatus.ABANDONED.is(plan.getStatus())) {
            throw new BusinessException("已废弃方案不能调整");
        }
        ensurePlanItemUnlocked(item.getId(), "该课程已锁定，不能调整");
        BigDecimal beforeScore = normalizeScore(plan.getTotalScore());

        Classroom classroom = classroomMapper.selectById(request.getClassroomId());
        if (classroom == null || Integer.valueOf(1).equals(classroom.getDeleted())) {
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

        boolean syncFormalSchedule = SchedulePlanStatus.APPLIED.is(refreshedPlan.getStatus());
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
        log.setAdjustReason(adjustReason);
        explainService.appendAdjustLog(log);

        return new AdjustPlanResultVo(
                refreshedItem.getId(),
                refreshedPlan.getId(),
                log.getBeforeScore(),
                log.getAfterScore(),
                refreshedItem.getConflictFlag() == null ? 0 : refreshedItem.getConflictFlag(),
                refreshedItem.getConflictReason(),
                syncFormalSchedule,
                scheduleId,
                syncFormalSchedule ? "已同步正式课表" : "仅更新方案草稿");
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
        Map<Long, List<String>> peerConflictReasons = buildPeerConflictReasons(items, teacherMap, classMap, roomMap);

        int conflictCount = 0;
        for (SchedulePlanItem item : items) {
            List<String> reasons = buildConflictReasons(item, peerConflictReasons.getOrDefault(item.getId(), List.of()),
                    taskMap, courseMap, teacherMap, classMap, roomMap, slotMap);
            Integer conflictFlag = reasons.isEmpty() ? 0 : 1;
            String conflictReason = reasons.isEmpty() ? null : String.join("；", reasons);
            if (!Objects.equals(item.getConflictFlag(), conflictFlag)
                    || !Objects.equals(item.getConflictReason(), conflictReason)) {
                item.setConflictFlag(conflictFlag);
                item.setConflictReason(conflictReason);
                item.setUpdatedAt(LocalDateTime.now());
                planItemMapper.updateById(item);
            }
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
    public ApplyPlanResultVo applyPlan(Long id) {
        return applyPlanWithAudit(id, SystemAuditLogService.ACTION_APPLY_PLAN);
    }

    private ApplyPlanResultVo applyPlanWithAudit(Long id, String actionType) {
        SchedulePlan plan = null;
        try {
            plan = planMapper.selectById(id);
            if (plan == null) {
                throw new BusinessException("排课方案不存在");
            }
            if (SchedulePlanStatus.SIMULATION.is(plan.getStatus()) || SchedulePlanStatus.CONFIRMED.is(plan.getStatus())) {
                throw new BusinessException("试算方案必须从试算详情页校验后应用");
            }
            if (SchedulePlanStatus.ABANDONED.is(plan.getStatus())) {
                throw new BusinessException("已废弃方案不能应用");
            }
            if (SchedulePlanStatus.DISCARDED.is(plan.getStatus())) {
                throw new BusinessException("已放弃试算方案不能应用");
            }
            if (SchedulePlanStatus.APPLIED.is(plan.getStatus())) {
                throw new BusinessException("该方案已应用，无需重复应用");
            }
            ApplyPlanResultVo result = applyPlanInternal(plan);
            auditLogService.recordSuccess(
                    actionType,
                    SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                    plan.getId(),
                    plan.getSemesterId(),
                    plan.getId(),
                    "正式课表已应用，排课数=" + result.getAppliedCount());
            return result;
        } catch (RuntimeException ex) {
            recordApplyPlanFailure(actionType, id, plan, ex);
            throw ex;
        }
    }

    private void recordApplyPlanFailure(String actionType, Long requestedPlanId, SchedulePlan plan, RuntimeException ex) {
        try {
            auditLogService.recordFailure(
                    actionType,
                    SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                    plan == null ? requestedPlanId : plan.getId(),
                    plan == null ? null : plan.getSemesterId(),
                    plan == null ? requestedPlanId : plan.getId(),
                    auditErrorCode(ex),
                    ex.getMessage());
        } catch (Exception ignored) {
            // 审计写入失败不能掩盖原始业务异常。
        }
    }

    private String auditErrorCode(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            return String.valueOf(businessException.getCode());
        }
        return ex.getClass().getSimpleName();
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplyPlanResultVo applySimulationPlan(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("试算方案不存在");
        }
        if (!SchedulePlanStatus.CONFIRMED.is(plan.getStatus())) {
            throw new BusinessException("试算方案确认后才能应用");
        }
        if (plan.getRepairTaskId() == null) {
            throw new BusinessException("试算方案缺少修复任务绑定，不能应用");
        }
        return applyPlanInternal(plan);
    }

    private ApplyPlanResultVo applyPlanInternal(SchedulePlan plan) {
        if (plan.getScheduledCount() == null || plan.getScheduledCount() == 0) {
            throw new BusinessException("该方案没有排课明细，无法应用");
        }

        assertNoConflictsBeforeApply(plan.getId());
        Long semesterId = plan.getSemesterId();

        List<SchedulePlan> oldAppliedPlans = planMapper.selectList(
                new LambdaQueryWrapper<SchedulePlan>()
                        .eq(SchedulePlan::getSemesterId, semesterId)
                        .eq(SchedulePlan::getStatus, SchedulePlanStatus.APPLIED.getCode()));
        ensurePlansUnlocked(oldAppliedPlans, "存在已锁定课程，不能被新方案覆盖，请先解锁");
        ensureSemesterSchedulesUnlocked(semesterId, "存在已锁定课程，不能被新方案覆盖，请先解锁");
        scheduleMapper.delete(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId));
        for (SchedulePlan oldPlan : oldAppliedPlans) {
            scheduleMapper.delete(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getSemesterId, semesterId)
                    .eq(Schedule::getPlanId, oldPlan.getId()));
            oldPlan.setStatus(SchedulePlanStatus.DRAFT.getCode());
            oldPlan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(oldPlan);
        }

        List<SchedulePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, plan.getId()));

        Map<String, Long> timeSlotMap = timeSlotMapper.selectList(null).stream()
                .collect(Collectors.toMap(
                        ts -> ts.getDayOfWeek() + "_" + ts.getPeriodNo(),
                        TimeSlot::getId,
                        (a, b) -> a));

        int insertedCount = 0;
        for (SchedulePlanItem item : items) {
            validatePeriodPair(item);
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

        plan.setStatus(SchedulePlanStatus.APPLIED.getCode());
        plan.setAppliedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);

        return new ApplyPlanResultVo(plan.getId(), semesterId, insertedCount, plan.getAppliedAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplyPlanResultVo rollbackPlan(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if (SchedulePlanStatus.ABANDONED.is(plan.getStatus())) {
            throw new BusinessException("已废弃方案不能回滚应用");
        }
        if (plan.getScheduledCount() == null || plan.getScheduledCount() == 0) {
            throw new BusinessException("该方案没有排课明细，无法回滚应用");
        }

        if (SchedulePlanStatus.APPLIED.is(plan.getStatus())) {
            return new ApplyPlanResultVo(
                    plan.getId(), plan.getSemesterId(), 0, plan.getAppliedAt(), "目标方案已是当前应用方案");
        }

        // 回滚语义：将目标方案重新应用为正式课表（而不是只删除当前正式课表）。
        return applyPlanWithAudit(id, SystemAuditLogService.ACTION_ROLLBACK_PLAN);
    }

    private void assertNoConflictsBeforeApply(Long planId) {
        int conflictCount = refreshPlanConflictState(planId);
        if (conflictCount > 0) {
            throw new BusinessException("方案存在冲突，请先处理后再应用");
        }
    }

    private void ensurePlanItemUnlocked(Long planItemId, String message) {
        lockGuardService.ensurePlanItemUnlocked(planItemId, message);
    }

    private void ensureScheduleUnlocked(Long scheduleId, String message) {
        lockGuardService.ensureScheduleUnlocked(scheduleId, message);
    }

    private void ensurePlansUnlocked(List<SchedulePlan> plans, String message) {
        List<Long> planIds = plans.stream()
                .map(SchedulePlan::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (planIds.isEmpty()) {
            return;
        }
        Long count = scheduleLockedItemMapper.selectCount(new LambdaQueryWrapper<ScheduleLockedItem>()
                .in(ScheduleLockedItem::getPlanId, planIds)
                .eq(ScheduleLockedItem::getActiveFlag, 1));
        if (count != null && count > 0) {
            throw new BusinessException(message);
        }
    }

    private void ensureSemesterSchedulesUnlocked(Long semesterId, String message) {
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId));
        List<Long> scheduleIds = schedules.stream()
                .map(Schedule::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (scheduleIds.isEmpty()) {
            return;
        }
        Long count = scheduleLockedItemMapper.selectCount(new LambdaQueryWrapper<ScheduleLockedItem>()
                .in(ScheduleLockedItem::getScheduleId, scheduleIds)
                .eq(ScheduleLockedItem::getActiveFlag, 1));
        if (count != null && count > 0) {
            throw new BusinessException(message);
        }
    }

    private void fillItemRelations(List<SchedulePlanItemVo> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<Long, Course> courseMap = courseMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItemVo::getCourseId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItemVo::getTeacherId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        Map<Long, ClassInfo> classMap = classInfoMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItemVo::getClassId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));
        Map<Long, Classroom> roomMap = classroomMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItemVo::getClassroomId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));

        for (SchedulePlanItemVo item : items) {
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

    /** 委托 SchedulePlanItemVo.fromEntity 逐字段拷贝持久化列（view 字段由 fillItemRelations 填充）。 */
    private SchedulePlanItemVo planItemToVo(SchedulePlanItem entity) {
        return SchedulePlanItemVo.fromEntity(entity);
    }

    private void fillPlanDisplayFields(List<SchedulePlanVo> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }

        List<Long> semesterIds = plans.stream()
                .map(SchedulePlanVo::getSemesterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Semester> semesterMap = semesterIds.isEmpty()
                ? Map.of()
                : semesterMapper.selectBatchIds(semesterIds)
                .stream()
                .collect(Collectors.toMap(Semester::getId, Function.identity(), (a, b) -> a));

        for (SchedulePlanVo plan : plans) {
            Semester semester = semesterMap.get(plan.getSemesterId());
            plan.setSemesterName(semester != null ? semester.getName() : null);
            plan.setStrategyName(strategyName(plan.getStrategyType()));
        }
    }

    private String strategyName(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "TEACHER_PRIORITY" -> "教师优先";
            case "CLASS_BALANCE" -> "班级均衡";
            case "CLASSROOM_UTILIZATION" -> "教室利用率";
            case "COMPREHENSIVE" -> "综合最优";
            default -> type;
        };
    }

    private List<String> buildConflictReasons(
            SchedulePlanItem item,
            List<String> peerConflictReasons,
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
        reasons.addAll(peerConflictReasons);

        return reasons.stream().distinct().collect(Collectors.toList());
    }

    private Map<Long, List<String>> buildPeerConflictReasons(
            List<SchedulePlanItem> items,
            Map<Long, Teacher> teacherMap,
            Map<Long, ClassInfo> classMap,
            Map<Long, Classroom> roomMap
    ) {
        Map<String, List<SchedulePlanItem>> itemsByTime = new LinkedHashMap<>();
        for (SchedulePlanItem item : items) {
            if (item.getId() == null || item.getWeekday() == null || item.getStartPeriod() == null) {
                continue;
            }
            String key = item.getWeekday() + "_" + item.getStartPeriod();
            itemsByTime.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        Map<Long, List<String>> reasonsByItemId = new LinkedHashMap<>();
        for (List<SchedulePlanItem> timeItems : itemsByTime.values()) {
            if (timeItems.size() <= 1) {
                continue;
            }
            addGroupedConflictReasons(timeItems, reasonsByItemId, SchedulePlanItem::getTeacherId,
                    teacherId -> "教师时间冲突：" + safeName(teacherMap.get(teacherId) == null ? null : teacherMap.get(teacherId).getName()));
            addGroupedConflictReasons(timeItems, reasonsByItemId, SchedulePlanItem::getClassId,
                    classId -> "班级时间冲突：" + safeName(classMap.get(classId) == null ? null : classMap.get(classId).getClassName()));
            addGroupedConflictReasons(timeItems, reasonsByItemId, SchedulePlanItem::getClassroomId,
                    classroomId -> "教室时间冲突：" + safeName(roomMap.get(classroomId) == null ? null : roomMap.get(classroomId).getRoomName()));
        }
        return reasonsByItemId;
    }

    private void addGroupedConflictReasons(
            List<SchedulePlanItem> items,
            Map<Long, List<String>> reasonsByItemId,
            Function<SchedulePlanItem, Long> keyExtractor,
            Function<Long, String> reasonBuilder
    ) {
        Map<Long, List<SchedulePlanItem>> grouped = new LinkedHashMap<>();
        for (SchedulePlanItem item : items) {
            grouped.computeIfAbsent(keyExtractor.apply(item), ignored -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<Long, List<SchedulePlanItem>> entry : grouped.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            String reason = reasonBuilder.apply(entry.getKey());
            for (SchedulePlanItem item : entry.getValue()) {
                reasonsByItemId.computeIfAbsent(item.getId(), ignored -> new ArrayList<>()).add(reason);
            }
        }
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
                .eq(Schedule::getSemesterId, plan.getSemesterId())
                .eq(Schedule::getPlanId, plan.getId())
                .eq(Schedule::getTeachingTaskId, before.getTeachingTaskId())
                .eq(Schedule::getTimeSlotId, oldSlot.getId()));
        if (schedules.isEmpty()) {
            throw new BusinessException("已应用方案缺少对应正式课表记录，无法同步");
        }
        Schedule schedule = schedules.get(0);
        ensureScheduleUnlocked(schedule.getId(), "该课程已锁定，不能同步正式课表");
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

    /**
     * 与 V4ScheduleAdjustmentService.resolveTimeSlot 对齐：
     * startPeriod 必须为奇数（1/3/5/7/9），endPeriod 必须等于 startPeriod+1，
     * 否则 (startPeriod+1)/2 算出的 periodNo 会把偶数节静默错配到下一节大节。
     */
    private void validatePeriodPair(SchedulePlanItem item) {
        Integer startPeriod = item.getStartPeriod();
        Integer endPeriod = item.getEndPeriod();
        Integer weekday = item.getWeekday();
        if (weekday == null || startPeriod == null || endPeriod == null) {
            throw new BusinessException("方案 item 缺少 weekday / startPeriod / endPeriod 字段");
        }
        if (startPeriod % 2 == 0 || endPeriod - startPeriod != 1) {
            throw new BusinessException("方案 item 节次非法：周" + weekday
                    + " 第" + startPeriod + "-" + endPeriod + "节，startPeriod 须为奇数且与 endPeriod 相邻");
        }
    }
}
