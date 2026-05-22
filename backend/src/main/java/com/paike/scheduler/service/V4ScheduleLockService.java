package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.dto.ScheduleLockRequest;
import com.paike.scheduler.service.vo.ScheduleLockActionVo;
import com.paike.scheduler.service.vo.ScheduleLockItemVo;
import com.paike.scheduler.service.vo.ScheduleLockListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class V4ScheduleLockService {

    private static final String TARGET_PLAN = "PLAN";
    private static final String TARGET_SCHEDULE = "SCHEDULE";

    private final ScheduleLockedItemMapper scheduleLockedItemMapper;
    private final SchedulePlanMapper schedulePlanMapper;
    private final SchedulePlanItemMapper schedulePlanItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final SchedulePlanService schedulePlanService;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TransactionTemplate transactionTemplate;
    private final Object lockMutationMutex = new Object();

    public ScheduleLockActionVo lock(ScheduleLockRequest request) {
        return runLockMutation(() -> lockInternal(request));
    }

    private ScheduleLockActionVo lockInternal(ScheduleLockRequest request) {
        String targetType = normalizeTargetType(request.getTargetType());
        ResolvedTarget target = resolveTarget(request, targetType);
        String lockReason = trimToNull(request.getLockReason());
        if (lockReason == null) {
            throw new BusinessException("锁定原因不能为空");
        }

        ScheduleLockedItem existing = findActiveLock(targetType, target.planItemId, target.scheduleId);
        if (existing != null) {
            throw new BusinessException("该课程已处于锁定状态");
        }

        ScheduleLockedItem record = new ScheduleLockedItem();
        record.setTargetType(targetType);
        record.setPlanId(target.planId);
        record.setPlanItemId(target.planItemId);
        record.setScheduleId(target.scheduleId);
        record.setLockReason(lockReason);
        record.setActiveFlag(1);
        try {
            scheduleLockedItemMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该课程已处于锁定状态");
        }

        ScheduleLockActionVo result = new ScheduleLockActionVo();
        result.setLocked(true);
        result.setUnlocked(false);
        result.setLockId(record.getId());
        result.setPlanId(record.getPlanId());
        result.setPlanItemId(record.getPlanItemId());
        result.setScheduleId(record.getScheduleId());
        result.setMessage("课程已锁定");
        return result;
    }

    public ScheduleLockActionVo unlock(ScheduleLockRequest request) {
        return runLockMutation(() -> unlockInternal(request));
    }

    private ScheduleLockActionVo unlockInternal(ScheduleLockRequest request) {
        String targetType = normalizeTargetType(request.getTargetType());
        ResolvedTarget target = resolveTarget(request, targetType);
        ScheduleLockedItem existing = findActiveLock(targetType, target.planItemId, target.scheduleId);
        if (existing == null) {
            throw new BusinessException("该课程当前未锁定");
        }

        existing.setActiveFlag(0);
        existing.setUnlockedAt(LocalDateTime.now());
        scheduleLockedItemMapper.updateById(existing);

        ScheduleLockActionVo result = new ScheduleLockActionVo();
        result.setLocked(false);
        result.setUnlocked(true);
        result.setLockId(existing.getId());
        result.setPlanId(existing.getPlanId());
        result.setPlanItemId(existing.getPlanItemId());
        result.setScheduleId(existing.getScheduleId());
        result.setMessage("课程已取消锁定");
        return result;
    }

    private <T> T runLockMutation(Supplier<T> action) {
        synchronized (lockMutationMutex) {
            return Objects.requireNonNull(transactionTemplate.execute(status -> action.get()));
        }
    }

    public ScheduleLockListVo listPlanLocks(Long planId) {
        SchedulePlan plan = schedulePlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }

        List<ScheduleLockedItem> locks = scheduleLockedItemMapper.selectList(
                new LambdaQueryWrapper<ScheduleLockedItem>()
                        .eq(ScheduleLockedItem::getPlanId, planId)
                        .eq(ScheduleLockedItem::getActiveFlag, 1)
                        .orderByDesc(ScheduleLockedItem::getCreatedAt)
                        .orderByDesc(ScheduleLockedItem::getId));

        Map<Long, SchedulePlanItem> planItemMap = schedulePlanService.getPlanItems(planId).stream()
                .collect(Collectors.toMap(SchedulePlanItem::getId, Function.identity()));
        Map<Long, Schedule> scheduleMap = loadScheduleMap(locks);
        RelationContext relations = buildRelationContext(List.of(), scheduleMap.values());

        ScheduleLockListVo result = new ScheduleLockListVo();
        result.setPlanId(planId);
        result.setPlanName(plan.getName());
        result.setLockedCount(locks.size());
        result.setItems(locks.stream()
                .map(lock -> toVo(lock, planItemMap.get(lock.getPlanItemId()), scheduleMap.get(lock.getScheduleId()), relations))
                .toList());
        return result;
    }

    private ScheduleLockItemVo toVo(
            ScheduleLockedItem lock,
            SchedulePlanItem planItem,
            Schedule schedule,
            RelationContext relations
    ) {
        ScheduleLockItemVo item = new ScheduleLockItemVo();
        item.setLockId(lock.getId());
        item.setTargetType(lock.getTargetType());
        item.setPlanId(lock.getPlanId());
        item.setPlanItemId(lock.getPlanItemId());
        item.setScheduleId(lock.getScheduleId());
        item.setLockReason(lock.getLockReason());
        item.setCreatedAt(lock.getCreatedAt());

        if (planItem != null) {
            item.setTeachingTaskId(planItem.getTeachingTaskId());
            item.setCourseName(planItem.getCourseName());
            item.setTeacherName(planItem.getTeacherName());
            item.setClassName(planItem.getClassName());
            item.setWeekDay(planItem.getWeekday());
            item.setPeriod(formatPeriod(planItem.getStartPeriod(), planItem.getEndPeriod()));
            item.setRoomName(planItem.getRoomName());
            return item;
        }

        if (schedule != null) {
            item.setTeachingTaskId(schedule.getTeachingTaskId());
            item.setCourseName(nameOf(relations.courseMap, schedule.getCourseId(), Course::getCourseName));
            item.setTeacherName(nameOf(relations.teacherMap, schedule.getTeacherId(), Teacher::getName));
            item.setClassName(nameOf(relations.classMap, schedule.getClassId(), ClassInfo::getClassName));
            item.setRoomName(nameOf(relations.roomMap, schedule.getClassroomId(), Classroom::getRoomName));
            TimeSlot timeSlot = relations.timeSlotMap.get(schedule.getTimeSlotId());
            if (timeSlot != null) {
                item.setWeekDay(timeSlot.getDayOfWeek());
                item.setPeriod(formatPeriod(timeSlot.getPeriodNo(), timeSlot.getPeriodNo()));
            }
        }
        return item;
    }

    private ScheduleLockedItem findActiveLock(String targetType, Long planItemId, Long scheduleId) {
        LambdaQueryWrapper<ScheduleLockedItem> wrapper = new LambdaQueryWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getTargetType, targetType)
                .eq(ScheduleLockedItem::getActiveFlag, 1);
        if (TARGET_PLAN.equals(targetType)) {
            wrapper.eq(ScheduleLockedItem::getPlanItemId, planItemId);
        } else {
            wrapper.eq(ScheduleLockedItem::getScheduleId, scheduleId);
        }
        return scheduleLockedItemMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    private ResolvedTarget resolveTarget(ScheduleLockRequest request, String targetType) {
        if (TARGET_PLAN.equals(targetType)) {
            return resolvePlanTarget(request);
        }
        if (TARGET_SCHEDULE.equals(targetType)) {
            return resolveScheduleTarget(request);
        }
        throw new BusinessException("不支持的锁定目标类型");
    }

    private ResolvedTarget resolvePlanTarget(ScheduleLockRequest request) {
        if (request.getPlanId() == null) {
            throw new BusinessException("排课方案 ID 不能为空");
        }
        if (request.getPlanItemId() == null) {
            throw new BusinessException("方案明细 ID 不能为空");
        }
        SchedulePlan plan = schedulePlanMapper.selectById(request.getPlanId());
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if ("ABANDONED".equalsIgnoreCase(plan.getStatus())) {
            throw new BusinessException("已废弃方案不能锁定");
        }
        SchedulePlanItem item = schedulePlanItemMapper.selectById(request.getPlanItemId());
        if (item == null || !Objects.equals(item.getPlanId(), request.getPlanId())) {
            throw new BusinessException("方案明细不存在或不属于当前方案");
        }
        ResolvedTarget target = new ResolvedTarget();
        target.planId = plan.getId();
        target.planItemId = item.getId();
        return target;
    }

    private ResolvedTarget resolveScheduleTarget(ScheduleLockRequest request) {
        if (request.getScheduleId() == null) {
            throw new BusinessException("正式课表记录 ID 不能为空");
        }
        Schedule schedule = scheduleMapper.selectById(request.getScheduleId());
        if (schedule == null || Integer.valueOf(1).equals(schedule.getDeleted())) {
            throw new BusinessException("正式课表记录不存在");
        }
        Long planId = request.getPlanId() != null ? request.getPlanId() : schedule.getPlanId();
        if (planId == null) {
            throw new BusinessException("正式课表缺少来源方案，暂不支持锁定");
        }
        if (request.getPlanId() != null && schedule.getPlanId() != null && !Objects.equals(request.getPlanId(), schedule.getPlanId())) {
            throw new BusinessException("正式课表来源方案与请求参数不一致");
        }
        ResolvedTarget target = new ResolvedTarget();
        target.planId = planId;
        target.scheduleId = schedule.getId();
        return target;
    }

    private Map<Long, Schedule> loadScheduleMap(List<ScheduleLockedItem> locks) {
        List<Long> scheduleIds = locks.stream()
                .map(ScheduleLockedItem::getScheduleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (scheduleIds.isEmpty()) {
            return new HashMap<>();
        }
        return scheduleMapper.selectBatchIds(scheduleIds).stream()
                .collect(Collectors.toMap(Schedule::getId, Function.identity()));
    }

    private RelationContext buildRelationContext(Collection<SchedulePlanItem> planItems, Collection<Schedule> schedules) {
        RelationContext context = new RelationContext();
        List<Long> courseIds = collectIds(planItems, schedules, SchedulePlanItem::getCourseId, Schedule::getCourseId);
        List<Long> teacherIds = collectIds(planItems, schedules, SchedulePlanItem::getTeacherId, Schedule::getTeacherId);
        List<Long> classIds = collectIds(planItems, schedules, SchedulePlanItem::getClassId, Schedule::getClassId);
        List<Long> roomIds = collectIds(planItems, schedules, SchedulePlanItem::getClassroomId, Schedule::getClassroomId);
        List<Long> timeSlotIds = schedules.stream()
                .map(Schedule::getTimeSlotId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        context.courseMap = courseIds.isEmpty() ? Map.of() : courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        context.teacherMap = teacherIds.isEmpty() ? Map.of() : teacherMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity()));
        context.classMap = classIds.isEmpty() ? Map.of() : classInfoMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassInfo::getId, Function.identity()));
        context.roomMap = roomIds.isEmpty() ? Map.of() : classroomMapper.selectBatchIds(roomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity()));
        context.timeSlotMap = timeSlotIds.isEmpty() ? Map.of() : timeSlotMapper.selectBatchIds(timeSlotIds).stream()
                .collect(Collectors.toMap(TimeSlot::getId, Function.identity()));
        return context;
    }

    private <T> List<Long> collectIds(
            Collection<SchedulePlanItem> planItems,
            Collection<Schedule> schedules,
            Function<SchedulePlanItem, Long> planFunc,
            Function<Schedule, Long> scheduleFunc
    ) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (SchedulePlanItem item : planItems) {
            Long id = planFunc.apply(item);
            if (id != null) {
                ids.add(id);
            }
        }
        for (Schedule schedule : schedules) {
            Long id = scheduleFunc.apply(schedule);
            if (id != null) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    private <T> String nameOf(Map<Long, T> map, Long id, Function<T, String> nameFunc) {
        if (id == null) {
            return null;
        }
        T item = map.get(id);
        return item == null ? null : nameFunc.apply(item);
    }

    private String formatPeriod(Integer startPeriod, Integer endPeriod) {
        if (startPeriod == null) {
            return null;
        }
        if (endPeriod == null || Objects.equals(startPeriod, endPeriod)) {
            return String.valueOf(startPeriod);
        }
        return startPeriod + "-" + endPeriod;
    }

    private String normalizeTargetType(String targetType) {
        String normalized = trimToNull(targetType);
        if (normalized == null) {
            throw new BusinessException("锁定目标类型不能为空");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class ResolvedTarget {
        private Long planId;
        private Long planItemId;
        private Long scheduleId;
    }

    private static class RelationContext {
        private Map<Long, Course> courseMap = Map.of();
        private Map<Long, Teacher> teacherMap = Map.of();
        private Map<Long, ClassInfo> classMap = Map.of();
        private Map<Long, Classroom> roomMap = Map.of();
        private Map<Long, TimeSlot> timeSlotMap = Map.of();
    }
}
