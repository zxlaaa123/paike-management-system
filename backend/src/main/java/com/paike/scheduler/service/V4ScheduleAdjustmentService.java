package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.common.enums.SchedulePlanStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.dto.SchedulePlanItemAdjustRequest;
import com.paike.scheduler.service.dto.V4ScheduleAdjustmentRequest;
import com.paike.scheduler.service.vo.AdjustPlanResultVo;
import com.paike.scheduler.service.vo.ScheduleAdjustmentApplyVo;
import com.paike.scheduler.service.vo.ScheduleAdjustmentCheckVo;
import com.paike.scheduler.service.vo.ScheduleAdjustmentIssueVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class V4ScheduleAdjustmentService {

    private static final String TARGET_PLAN_ITEM = "PLAN_ITEM";
    private static final String TARGET_SCHEDULE = "SCHEDULE";

    private final SchedulePlanService schedulePlanService;
    private final SchedulePlanExplainService schedulePlanExplainService;
    private final SchedulePlanMapper planMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ScheduleLockGuardService lockGuardService;
    private final TeacherUnavailableTimeService unavailableTimeService;
    private final TransactionTemplate transactionTemplate;
    private final SystemAuditLogService auditLogService;
    private final Object adjustmentMutationMutex = new Object();

    public ScheduleAdjustmentCheckVo checkAdjustment(V4ScheduleAdjustmentRequest request) {
        AdjustmentContext context = resolveContext(request);
        ensureTargetUnlocked(context);
        Classroom newRoom = loadAvailableRoom(request.getNewRoomId());
        TimeSlot newSlot = resolveTimeSlot(request.getNewWeekDay(), request.getNewPeriodStart(), request.getNewPeriodEnd());
        if (newSlot == null) {
            throw new BusinessException("所选时间段不存在");
        }
        if (isSameTarget(context, request)) {
            throw new BusinessException("调整后的时间和教室与原记录一致");
        }

        List<ScheduleAdjustmentIssueVo> issues = TARGET_PLAN_ITEM.equals(context.targetType)
                ? checkPlanItemIssues(context, newRoom, newSlot, request)
                : checkScheduleIssues(context, newRoom, newSlot);

        ScheduleAdjustmentCheckVo result = new ScheduleAdjustmentCheckVo();
        result.setTargetType(context.targetType);
        result.setPlanId(context.plan != null ? context.plan.getId() : context.schedule != null ? context.schedule.getPlanId() : null);
        result.setPlanItemId(context.planItem != null ? context.planItem.getId() : null);
        result.setScheduleId(context.schedule != null ? context.schedule.getId() : null);
        result.setCourseName(context.course != null ? context.course.getCourseName() : null);
        result.setTeacherName(context.teacher != null ? context.teacher.getName() : null);
        result.setClassName(context.classInfo != null ? context.classInfo.getClassName() : null);
        result.setCurrentRoomId(context.currentRoom != null ? context.currentRoom.getId() : null);
        result.setCurrentRoomName(context.currentRoom != null ? context.currentRoom.getRoomName() : null);
        result.setCurrentWeekDay(context.currentWeekDay);
        result.setCurrentPeriodStart(context.currentStartPeriod);
        result.setCurrentPeriodEnd(context.currentPeriodEnd);
        result.setCurrentTimeLabel(buildTimeLabel(context.currentWeekDay, context.currentStartPeriod, context.currentPeriodEnd, context.currentSlot));
        result.setNewRoomId(newRoom.getId());
        result.setNewRoomName(newRoom.getRoomName());
        result.setNewWeekDay(request.getNewWeekDay());
        result.setNewPeriodStart(request.getNewPeriodStart());
        result.setNewPeriodEnd(request.getNewPeriodEnd());
        result.setNewTimeLabel(buildTimeLabel(request.getNewWeekDay(), request.getNewPeriodStart(), request.getNewPeriodEnd(), newSlot));
        result.setIssues(issues);
        result.setIssueCount(issues.size());
        result.setBlockingIssueCount((int) issues.stream().filter(issue -> Boolean.TRUE.equals(issue.getBlocking())).count());
        result.setHasConflict(result.getBlockingIssueCount() > 0);
        result.setCanApply(result.getBlockingIssueCount() == 0);
        return result;
    }

    public ScheduleAdjustmentApplyVo applyAdjustment(V4ScheduleAdjustmentRequest request) {
        try {
            ScheduleAdjustmentApplyVo result = runAdjustmentMutation(() -> applyAdjustmentInternal(request));
            if (Boolean.TRUE.equals(result.getSaved())) {
                auditLogService.recordSuccess(
                        SystemAuditLogService.ACTION_ADJUST_SCHEDULE,
                        auditTargetType(request),
                        auditTargetId(result),
                        null,
                        result.getPlanId(),
                        "局部调整成功：" + result.getMessage());
            }
            return result;
        } catch (RuntimeException ex) {
            auditLogService.recordFailure(
                    SystemAuditLogService.ACTION_ADJUST_SCHEDULE,
                    auditTargetType(request),
                    auditTargetId(request),
                    null,
                    request == null ? null : request.getPlanId(),
                    auditErrorCode(ex),
                    ex.getMessage());
            throw ex;
        }
    }

    private ScheduleAdjustmentApplyVo applyAdjustmentInternal(V4ScheduleAdjustmentRequest request) {
        if (request.getAdjustReason() == null || request.getAdjustReason().trim().isEmpty()) {
            throw new BusinessException("调整原因不能为空");
        }

        ScheduleAdjustmentCheckVo checkResult = checkAdjustment(request);
        ScheduleAdjustmentApplyVo result = new ScheduleAdjustmentApplyVo();
        result.setPlanId(checkResult.getPlanId());
        result.setPlanItemId(checkResult.getPlanItemId());
        result.setScheduleId(checkResult.getScheduleId());
        result.setCheckResult(checkResult);

        if (Boolean.TRUE.equals(checkResult.getHasConflict()) && !Boolean.TRUE.equals(request.getForceAdjust())) {
            result.setSaved(false);
            result.setRequiresConfirmation(true);
            result.setSyncFormalSchedule(false);
            result.setSyncPlanItem(false);
            result.setMessage("检测到冲突，请确认是否强制保存");
            return result;
        }

        AdjustmentContext context = resolveContext(request);
        if (TARGET_PLAN_ITEM.equals(context.targetType)) {
            AdjustPlanResultVo saved = schedulePlanService.adjustPlanItem(context.planItem.getId(), toPlanAdjustRequest(request));
            result.setSaved(true);
            result.setRequiresConfirmation(false);
            result.setSyncFormalSchedule(Boolean.TRUE.equals(saved.getSyncFormalSchedule()));
            result.setSyncPlanItem(true);
            result.setScheduleId(asLong(saved.getScheduleId(), context.schedule != null ? context.schedule.getId() : null));
            result.setMessage(Boolean.TRUE.equals(request.getForceAdjust()) && Boolean.TRUE.equals(checkResult.getHasConflict())
                    ? "已强制保存调整，并保留冲突记录"
                    : asString(saved.getMessage(), "调整成功"));
            return result;
        }

        applyToSchedule(context, request, checkResult);
        result.setSaved(true);
        result.setRequiresConfirmation(false);
        result.setSyncFormalSchedule(true);
        result.setSyncPlanItem(false);
        result.setMessage(Boolean.TRUE.equals(request.getForceAdjust()) && Boolean.TRUE.equals(checkResult.getHasConflict())
                ? "已强制保存正式课表调整，并记录调整日志"
                : "正式课表调整成功");
        return result;
    }

    private <T> T runAdjustmentMutation(Supplier<T> action) {
        synchronized (adjustmentMutationMutex) {
            return Objects.requireNonNull(transactionTemplate.execute(status -> action.get()));
        }
    }

    private void applyToSchedule(AdjustmentContext context, V4ScheduleAdjustmentRequest request, ScheduleAdjustmentCheckVo checkResult) {
        if (context.schedule == null) {
            throw new BusinessException("正式课表记录不存在");
        }

        if (context.schedule.getPlanId() != null) {
            SchedulePlanItem linkedItem = matchPlanItem(context);
            if (linkedItem == null) {
                throw new BusinessException("无法定位对应方案明细，请改为从方案详情页发起调整");
            }
            SchedulePlan plan = planMapper.selectById(linkedItem.getPlanId());
            if (plan == null || SchedulePlanStatus.ABANDONED.is(plan.getStatus())) {
                throw new BusinessException("来源方案不可调整，请改为直接调整正式课表记录");
            }
            schedulePlanService.adjustPlanItem(linkedItem.getId(), toPlanAdjustRequest(request));
            return;
        }

        TimeSlot newSlot = resolveTimeSlot(request.getNewWeekDay(), request.getNewPeriodStart(), request.getNewPeriodEnd());
        if (newSlot == null) {
            throw new BusinessException("所选时间段不存在");
        }

        Schedule schedule = context.schedule;
        schedule.setClassroomId(request.getNewRoomId());
        schedule.setTimeSlotId(newSlot.getId());
        schedule.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(schedule);

        ScheduleAdjustLog log = new ScheduleAdjustLog();
        log.setPlanId(schedule.getPlanId());
        log.setScheduleId(schedule.getId());
        log.setSemesterId(schedule.getSemesterId());
        log.setTeachingTaskId(schedule.getTeachingTaskId());
        log.setOldClassroomId(context.currentRoom != null ? context.currentRoom.getId() : null);
        log.setOldWeekday(context.currentWeekDay);
        log.setOldStartPeriod(context.currentStartPeriod);
        log.setOldEndPeriod(context.currentPeriodEnd);
        log.setNewClassroomId(request.getNewRoomId());
        log.setNewWeekday(request.getNewWeekDay());
        log.setNewStartPeriod(request.getNewPeriodStart());
        log.setNewEndPeriod(request.getNewPeriodEnd());
        log.setBeforeScore(normalizeScore(context.plan != null ? context.plan.getTotalScore() : null));
        log.setAfterScore(normalizeScore(context.plan != null ? context.plan.getTotalScore() : null));
        log.setConflictFlag(Boolean.TRUE.equals(checkResult.getHasConflict()) ? 1 : 0);
        log.setAdjustReason(request.getAdjustReason().trim());
        schedulePlanExplainService.appendAdjustLog(log);
    }

    private List<ScheduleAdjustmentIssueVo> checkPlanItemIssues(
            AdjustmentContext context,
            Classroom newRoom,
            TimeSlot newSlot,
            V4ScheduleAdjustmentRequest request
    ) {
        List<ScheduleAdjustmentIssueVo> issues = new ArrayList<>();
        appendConstraintIssues(issues, context, newRoom, newSlot);

        List<SchedulePlanItem> siblingItems = planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, context.planItem.getPlanId())
                .eq(SchedulePlanItem::getWeekday, request.getNewWeekDay()));
        for (SchedulePlanItem sibling : siblingItems) {
            if (Objects.equals(sibling.getId(), context.planItem.getId())) {
                continue;
            }
            if (!isOverlapping(request.getNewPeriodStart(), request.getNewPeriodEnd(), sibling.getStartPeriod(), sibling.getEndPeriod())) {
                continue;
            }
            if (Objects.equals(sibling.getTeacherId(), context.teacher != null ? context.teacher.getId() : null)) {
                issues.add(blockingIssue("TEACHER_CONFLICT", "教师冲突", safeName(context.teacher != null ? context.teacher.getName() : null) + " 在该时段已有课程"));
            }
            if (Objects.equals(sibling.getClassId(), context.classInfo != null ? context.classInfo.getId() : null)) {
                issues.add(blockingIssue("CLASS_CONFLICT", "班级冲突", safeName(context.classInfo != null ? context.classInfo.getClassName() : null) + " 在该时段已有课程"));
            }
            if (Objects.equals(sibling.getClassroomId(), newRoom.getId())) {
                issues.add(blockingIssue("ROOM_CONFLICT", "教室冲突", safeName(newRoom.getRoomName()) + " 在该时段已被占用"));
            }
        }
        return distinctIssues(issues);
    }

    private List<ScheduleAdjustmentIssueVo> checkScheduleIssues(
            AdjustmentContext context,
            Classroom newRoom,
            TimeSlot newSlot
    ) {
        List<ScheduleAdjustmentIssueVo> issues = new ArrayList<>();
        appendConstraintIssues(issues, context, newRoom, newSlot);

        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, context.schedule.getSemesterId())
                .eq(Schedule::getTimeSlotId, newSlot.getId())
                .ne(Schedule::getId, context.schedule.getId()));

        for (Schedule other : schedules) {
            if (Objects.equals(other.getTeacherId(), context.teacher != null ? context.teacher.getId() : null)) {
                issues.add(blockingIssue("TEACHER_CONFLICT", "教师冲突", safeName(context.teacher != null ? context.teacher.getName() : null) + " 在该时段已有课程"));
            }
            if (Objects.equals(other.getClassId(), context.classInfo != null ? context.classInfo.getId() : null)) {
                issues.add(blockingIssue("CLASS_CONFLICT", "班级冲突", safeName(context.classInfo != null ? context.classInfo.getClassName() : null) + " 在该时段已有课程"));
            }
            if (Objects.equals(other.getClassroomId(), newRoom.getId())) {
                issues.add(blockingIssue("ROOM_CONFLICT", "教室冲突", safeName(newRoom.getRoomName()) + " 在该时段已被占用"));
            }
        }
        return distinctIssues(issues);
    }

    private void appendConstraintIssues(List<ScheduleAdjustmentIssueVo> issues, AdjustmentContext context, Classroom newRoom, TimeSlot newSlot) {
        if (context.teacher != null && unavailableTimeService.isUnavailable(context.teacher.getId(), newSlot.getId())) {
            issues.add(blockingIssue("TEACHER_UNAVAILABLE", "教师禁排", safeName(context.teacher.getName()) + " 在 " + safeName(newSlot.getTimeLabel()) + " 设置了禁排时间"));
        }
        if (context.classInfo != null
                && context.classInfo.getStudentCount() != null
                && newRoom.getCapacity() != null
                && context.classInfo.getStudentCount() > newRoom.getCapacity()) {
            issues.add(blockingIssue("ROOM_CAPACITY", "容量不足", safeName(context.classInfo.getClassName()) + " 人数为 " + context.classInfo.getStudentCount() + "，当前教室容量为 " + newRoom.getCapacity()));
        }
        if (context.course != null && CourseType.EXPERIMENT.getCode().equals(context.course.getCourseType())
                && !RoomType.LAB.getCode().equals(newRoom.getRoomType())) {
            issues.add(blockingIssue("ROOM_TYPE", "教室类型不匹配", "实验课必须安排在实验室"));
        }
        if (context.course != null && CourseType.COMPUTER.getCode().equals(context.course.getCourseType())
                && !RoomType.COMPUTER.getCode().equals(newRoom.getRoomType())) {
            issues.add(blockingIssue("ROOM_TYPE", "教室类型不匹配", "机房课必须安排在机房"));
        }
    }

    private ScheduleAdjustmentIssueVo blockingIssue(String issueType, String issueName, String message) {
        ScheduleAdjustmentIssueVo issue = new ScheduleAdjustmentIssueVo();
        issue.setIssueType(issueType);
        issue.setIssueName(issueName);
        issue.setBlocking(true);
        issue.setMessage(message);
        return issue;
    }

    private List<ScheduleAdjustmentIssueVo> distinctIssues(List<ScheduleAdjustmentIssueVo> issues) {
        Map<String, ScheduleAdjustmentIssueVo> map = new LinkedHashMap<>();
        for (ScheduleAdjustmentIssueVo issue : issues) {
            map.putIfAbsent(issue.getIssueType() + "|" + issue.getMessage(), issue);
        }
        return new ArrayList<>(map.values());
    }

    private AdjustmentContext resolveContext(V4ScheduleAdjustmentRequest request) {
        String targetType = request.getTargetType() == null ? "" : request.getTargetType().trim().toUpperCase(Locale.ROOT);
        if (TARGET_PLAN_ITEM.equals(targetType)) {
            return resolvePlanItemContext(request.getPlanItemId());
        }
        if (TARGET_SCHEDULE.equals(targetType)) {
            return resolveScheduleContext(request.getScheduleId());
        }
        throw new BusinessException("不支持的调整目标类型");
    }

    private AdjustmentContext resolvePlanItemContext(Long planItemId) {
        if (planItemId == null) {
            throw new BusinessException("方案明细 ID 不能为空");
        }
        SchedulePlanItem item = planItemMapper.selectById(planItemId);
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

        AdjustmentContext context = new AdjustmentContext();
        context.targetType = TARGET_PLAN_ITEM;
        context.plan = plan;
        context.planItem = item;
        context.course = item.getCourseId() != null ? courseMapper.selectById(item.getCourseId()) : null;
        context.teacher = item.getTeacherId() != null ? teacherMapper.selectById(item.getTeacherId()) : null;
        context.classInfo = item.getClassId() != null ? classInfoMapper.selectById(item.getClassId()) : null;
        context.currentRoom = item.getClassroomId() != null ? classroomMapper.selectById(item.getClassroomId()) : null;
        context.currentWeekDay = item.getWeekday();
        context.currentStartPeriod = item.getStartPeriod();
        context.currentPeriodEnd = item.getEndPeriod();
        context.currentSlot = resolveTimeSlot(item.getWeekday(), item.getStartPeriod(), item.getEndPeriod());
        return context;
    }

    private AdjustmentContext resolveScheduleContext(Long scheduleId) {
        if (scheduleId == null) {
            throw new BusinessException("正式课表记录 ID 不能为空");
        }
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || Integer.valueOf(1).equals(schedule.getDeleted())) {
            throw new BusinessException("正式课表记录不存在");
        }

        AdjustmentContext context = new AdjustmentContext();
        context.targetType = TARGET_SCHEDULE;
        context.schedule = schedule;
        context.plan = schedule.getPlanId() != null ? planMapper.selectById(schedule.getPlanId()) : null;
        context.course = schedule.getCourseId() != null ? courseMapper.selectById(schedule.getCourseId()) : null;
        context.teacher = schedule.getTeacherId() != null ? teacherMapper.selectById(schedule.getTeacherId()) : null;
        context.classInfo = schedule.getClassId() != null ? classInfoMapper.selectById(schedule.getClassId()) : null;
        context.currentRoom = schedule.getClassroomId() != null ? classroomMapper.selectById(schedule.getClassroomId()) : null;
        context.currentSlot = schedule.getTimeSlotId() != null ? timeSlotMapper.selectById(schedule.getTimeSlotId()) : null;
        if (context.currentSlot != null) {
            context.currentWeekDay = context.currentSlot.getDayOfWeek();
            context.currentStartPeriod = context.currentSlot.getPeriodNo() * 2 - 1;
            context.currentPeriodEnd = context.currentStartPeriod + 1;
        }
        return context;
    }

    private SchedulePlanItemAdjustRequest toPlanAdjustRequest(V4ScheduleAdjustmentRequest request) {
        SchedulePlanItemAdjustRequest adjustRequest = new SchedulePlanItemAdjustRequest();
        adjustRequest.setClassroomId(request.getNewRoomId());
        adjustRequest.setWeekday(request.getNewWeekDay());
        adjustRequest.setStartPeriod(request.getNewPeriodStart());
        adjustRequest.setEndPeriod(request.getNewPeriodEnd());
        String adjustReason = request.getAdjustReason();
        adjustRequest.setAdjustReason(adjustReason == null || adjustReason.trim().isEmpty()
                ? "调整预检"
                : adjustReason.trim());
        return adjustRequest;
    }

    private Classroom loadAvailableRoom(Long roomId) {
        if (roomId == null) {
            throw new BusinessException("新教室不能为空");
        }
        Classroom room = classroomMapper.selectById(roomId);
        if (room == null || Integer.valueOf(1).equals(room.getDeleted())) {
            throw new BusinessException("所选教室不存在");
        }
        if (room.getStatus() == null || room.getStatus() != 1) {
            throw new BusinessException("所选教室已停用，不能调整");
        }
        return room;
    }

    private TimeSlot resolveTimeSlot(Integer weekDay, Integer startPeriod, Integer endPeriod) {
        if (weekDay == null || startPeriod == null || endPeriod == null) {
            return null;
        }
        if (startPeriod % 2 == 0 || endPeriod - startPeriod != 1) {
            return null;
        }
        int periodNo = (startPeriod + 1) / 2;
        return timeSlotMapper.selectOne(new LambdaQueryWrapper<TimeSlot>()
                .eq(TimeSlot::getDayOfWeek, weekDay)
                .eq(TimeSlot::getPeriodNo, periodNo));
    }

    private boolean isSameTarget(AdjustmentContext context, V4ScheduleAdjustmentRequest request) {
        return Objects.equals(context.currentRoom != null ? context.currentRoom.getId() : null, request.getNewRoomId())
                && Objects.equals(context.currentWeekDay, request.getNewWeekDay())
                && Objects.equals(context.currentStartPeriod, request.getNewPeriodStart())
                && Objects.equals(context.currentPeriodEnd, request.getNewPeriodEnd());
    }

    private boolean isOverlapping(Integer startA, Integer endA, Integer startB, Integer endB) {
        if (startA == null || endA == null || startB == null || endB == null) {
            return false;
        }
        return startA <= endB && startB <= endA;
    }

    private String buildTimeLabel(Integer weekDay, Integer startPeriod, Integer endPeriod, TimeSlot slot) {
        if (slot != null && slot.getTimeLabel() != null && !slot.getTimeLabel().isBlank()) {
            return slot.getTimeLabel();
        }
        if (weekDay == null || startPeriod == null || endPeriod == null) {
            return "—";
        }
        return "周" + weekDay + " 第" + startPeriod + "-" + endPeriod + "节";
    }

    private String safeName(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            return null;
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private Long asLong(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private String asString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private SchedulePlanItem matchPlanItem(AdjustmentContext context) {
        if (context.schedule == null || context.schedule.getPlanId() == null) {
            return null;
        }
        List<SchedulePlanItem> items = planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, context.schedule.getPlanId())
                .eq(SchedulePlanItem::getTeachingTaskId, context.schedule.getTeachingTaskId()));
        if (items.isEmpty()) {
            return null;
        }
        if (context.currentWeekDay != null && context.currentStartPeriod != null && context.currentPeriodEnd != null) {
            List<SchedulePlanItem> exactMatches = items.stream()
                    .filter(item -> Objects.equals(item.getWeekday(), context.currentWeekDay)
                            && Objects.equals(item.getStartPeriod(), context.currentStartPeriod)
                            && Objects.equals(item.getEndPeriod(), context.currentPeriodEnd))
                    .toList();
            if (!exactMatches.isEmpty()) {
                return exactMatches.stream()
                        .filter(item -> Objects.equals(item.getClassroomId(), context.schedule.getClassroomId()))
                        .findFirst()
                        .orElse(exactMatches.get(0));
            }
        }
        return items.size() == 1 ? items.get(0) : null;
    }

    private void ensureTargetUnlocked(AdjustmentContext context) {
        if (TARGET_PLAN_ITEM.equals(context.targetType) && context.planItem != null) {
            lockGuardService.ensurePlanItemUnlocked(context.planItem.getId(), "该课程已锁定，不能调整");
        } else if (TARGET_SCHEDULE.equals(context.targetType) && context.schedule != null) {
            lockGuardService.ensureScheduleAndLinkedPlanUnlocked(context.schedule, "该课程已锁定，不能调整");
        }
    }

    private String auditTargetType(V4ScheduleAdjustmentRequest request) {
        String targetType = request == null || request.getTargetType() == null
                ? ""
                : request.getTargetType().trim().toUpperCase(Locale.ROOT);
        return TARGET_PLAN_ITEM.equals(targetType)
                ? SystemAuditLogService.TARGET_SCHEDULE_PLAN_ITEM
                : SystemAuditLogService.TARGET_SCHEDULE;
    }

    private Long auditTargetId(V4ScheduleAdjustmentRequest request) {
        if (request == null) return null;
        String targetType = request.getTargetType() == null ? "" : request.getTargetType().trim().toUpperCase(Locale.ROOT);
        return TARGET_PLAN_ITEM.equals(targetType) ? request.getPlanItemId() : request.getScheduleId();
    }

    private Long auditTargetId(ScheduleAdjustmentApplyVo result) {
        if (result == null) return null;
        return result.getPlanItemId() != null ? result.getPlanItemId() : result.getScheduleId();
    }

    private String auditErrorCode(RuntimeException ex) {
        return ex instanceof BusinessException ? "BUSINESS_ERROR" : "SYSTEM_ERROR";
    }

    private static class AdjustmentContext {
        private String targetType;
        private SchedulePlan plan;
        private SchedulePlanItem planItem;
        private Schedule schedule;
        private Course course;
        private Teacher teacher;
        private ClassInfo classInfo;
        private Classroom currentRoom;
        private TimeSlot currentSlot;
        private Integer currentWeekDay;
        private Integer currentStartPeriod;
        private Integer currentPeriodEnd;
    }
}
