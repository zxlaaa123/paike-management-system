package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.enums.ScheduleSourceType;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.service.dto.AutoScheduleRequest;
import com.paike.scheduler.service.dto.AutoScheduleResult;
import com.paike.scheduler.service.scheduling.ArrangeStats;
import com.paike.scheduler.service.scheduling.AssignmentAttempt;
import com.paike.scheduler.service.scheduling.FailReason;
import com.paike.scheduler.service.scheduling.RuleConfig;
import com.paike.scheduler.service.scheduling.SchedulingReferenceData;
import com.paike.scheduler.service.scheduling.SchedulingReferenceLoader;
import com.paike.scheduler.service.scheduling.SchedulingSupport;
import com.paike.scheduler.service.scheduling.TaskArrangeOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoScheduleService {

    /**
     * 自动排课主流程。
     * 核心思路是先把"更难排的任务"放前面，再按规则优先级尝试时间段和教室。
     * run() 只做顶层编排，每个阶段的细节下沉到私有方法 / scheduling 包内的 record。
     */
    private final AutoScheduleBatchService batchService;
    private final UnscheduledTaskService unscheduledTaskService;
    private final ScheduleConflictService conflictService;
    private final ScheduleRuleService ruleService;
    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final SchedulingReferenceLoader referenceLoader;
    private final SemesterService semesterService;
    private final ScheduleLockGuardService lockGuardService;

    @Transactional(rollbackFor = Exception.class)
    public AutoScheduleResult run(AutoScheduleRequest request) {
        Long semesterId = resolveSemesterId(request);
        clearSchedulesIfNeeded(request, semesterId);

        List<TeachingTask> targetTasks = loadTargetTasks(semesterId, request.getTaskIds());
        AutoScheduleBatch batch = batchService.createBatch(
                semesterId, targetTasks.size(), request.isClearOldAutoSchedule());

        SchedulingReferenceData refData = referenceLoader.loadForAutoSchedule();
        RuleConfig rules = loadRuleConfig();

        List<TeachingTask> sortedTasks = SchedulingSupport.sortTasks(
                targetTasks,
                refData.unavailableCountByTeacher(),
                refData.courseMap(),
                refData.classMap());

        ArrangeStats stats = arrangeAllTasks(sortedTasks, batch, semesterId, refData, rules);

        return finalizeBatch(batch, targetTasks.size(), stats);
    }

    // ========== 阶段方法 ==========

    private void clearSchedulesIfNeeded(AutoScheduleRequest request, Long semesterId) {
        if (request.isClearAllSchedule()) {
            LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getDeleted, 0)
                    .eq(Schedule::getSemesterId, semesterId);
            ensureSchedulesUnlocked(wrapper);
            scheduleMapper.delete(wrapper);
            unscheduledTaskService.clearBySemester(semesterId);
        } else if (request.isClearOldAutoSchedule()) {
            LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getSourceType, ScheduleSourceType.AUTO.getCode())
                    .eq(Schedule::getDeleted, 0)
                    .eq(Schedule::getSemesterId, semesterId);
            ensureSchedulesUnlocked(wrapper);
            scheduleMapper.delete(wrapper);
            unscheduledTaskService.clearBySemester(semesterId);
        }
    }

    private List<TeachingTask> loadTargetTasks(Long semesterId, List<Long> requestedTaskIds) {
        List<TeachingTask> allTasks = teachingTaskMapper.selectList(
                new LambdaQueryWrapper<TeachingTask>()
                        .eq(TeachingTask::getDeleted, 0)
                        .eq(TeachingTask::getStatus, 1)
                        .eq(TeachingTask::getSemesterId, semesterId));
        if (requestedTaskIds == null || requestedTaskIds.isEmpty()) {
            return allTasks;
        }
        return allTasks.stream()
                .filter(t -> requestedTaskIds.contains(t.getId()))
                .collect(Collectors.toList());
    }

    private RuleConfig loadRuleConfig() {
        return new RuleConfig(
                ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS"),
                ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS"),
                ruleService.getBoolValue("ALLOW_SAME_COURSE_SAME_DAY"));
    }

    private ArrangeStats arrangeAllTasks(
            List<TeachingTask> sortedTasks,
            AutoScheduleBatch batch,
            Long semesterId,
            SchedulingReferenceData refData,
            RuleConfig rules) {

        int generatedCount = 0;
        int successCount = 0;
        int failedCount = 0;

        for (TeachingTask task : sortedTasks) {
            TaskArrangeOutcome outcome = arrangeOneTask(task, batch, semesterId, refData, rules);
            generatedCount += outcome.generatedThisRun();

            if (outcome.fullyArranged()) {
                successCount++;
            } else {
                failedCount++;
                int totalScheduled = outcome.alreadyScheduled() + outcome.generatedThisRun();
                unscheduledTaskService.addUnscheduledTask(
                        batch.getId(), semesterId, task.getId(),
                        outcome.requiredSlots(), totalScheduled, outcome.remainingAfterAttempt(),
                        outcome.lastFail().code(), outcome.lastFail().message());
            }
        }
        return new ArrangeStats(generatedCount, successCount, failedCount);
    }

    /**
     * 单任务编排：
     *  1) 算需要排几节（requiredSlots - 已排）
     *  2) 校验 course/class 存在
     *  3) 预过滤候选教室（容量 + 房型）
     *  4) 循环尝试每节，遇第一次找不到 fit 就退出（保持原 break 语义）
     */
    private TaskArrangeOutcome arrangeOneTask(
            TeachingTask task,
            AutoScheduleBatch batch,
            Long semesterId,
            SchedulingReferenceData refData,
            RuleConfig rules) {

        Integer weekly = task.getWeeklyHours();
        int requiredSlots = weekly == null ? 0 : (int) Math.ceil(weekly / 2.0);
        int alreadyScheduled = countScheduledSlots(task.getId());
        int remainingSlots = requiredSlots - alreadyScheduled;

        if (remainingSlots <= 0) {
            return TaskArrangeOutcome.ofProgress(
                    requiredSlots, alreadyScheduled, 0, FailReason.unknown());
        }

        Course course = refData.courseMap().get(task.getCourseId());
        if (course == null) {
            return TaskArrangeOutcome.preFlightFailed(
                    requiredSlots, alreadyScheduled,
                    "COURSE_NOT_FOUND", "关联课程不存在或已删除");
        }
        ClassInfo classInfo = refData.classMap().get(task.getClassId());
        if (classInfo == null) {
            return TaskArrangeOutcome.preFlightFailed(
                    requiredSlots, alreadyScheduled,
                    "CLASS_NOT_FOUND", "关联班级不存在或已删除");
        }

        int studentCount = classInfo.getStudentCount() == null ? 0 : classInfo.getStudentCount();
        String courseType = course.getCourseType();
        List<Classroom> matchedRooms = refData.classrooms().stream()
                .filter(r -> r.getCapacity() >= studentCount)
                .filter(r -> SchedulingSupport.isRoomTypeMatched(courseType, r.getRoomType()))
                .sorted(Comparator.comparingInt(Classroom::getCapacity))
                .collect(Collectors.toList());
        if (matchedRooms.isEmpty()) {
            return TaskArrangeOutcome.preFlightFailed(
                    requiredSlots, alreadyScheduled,
                    "NO_MATCHED_CLASSROOM", "没有符合课程类型和容量要求的教室");
        }

        int generated = 0;
        FailReason lastFail = FailReason.unknown();
        Set<Integer> usedDays = new HashSet<>();

        for (int i = 0; i < remainingSlots; i++) {
            AssignmentAttempt attempt = tryFindFirstFitAssignment(
                    task, matchedRooms, usedDays, semesterId, refData, rules);

            if (!attempt.placed()) {
                lastFail = attempt.lastFail();
                break;
            }

            saveSchedule(task, attempt.slot(), attempt.room(), batch.getId());
            generated++;
            usedDays.add(attempt.slot().getDayOfWeek());
        }

        return TaskArrangeOutcome.ofProgress(requiredSlots, alreadyScheduled, generated, lastFail);
    }

    /**
     * for-slot → for-room 二层循环，找第一个无冲突的 (slot, room) 组合。
     * 任何一次失败都用最近一次的 FailReason 兜底返回。
     *
     * 不是纯函数：内部仍要打 DB 做 daily-limit 和冲突检测。
     */
    private AssignmentAttempt tryFindFirstFitAssignment(
            TeachingTask task,
            List<Classroom> matchedRooms,
            Set<Integer> usedDays,
            Long semesterId,
            SchedulingReferenceData refData,
            RuleConfig rules) {

        FailReason lastFail = FailReason.unknown();

        for (TimeSlot slot : refData.sortedTimeSlots()) {
            if (refData.unavailableKeySet().contains(task.getTeacherId() + "_" + slot.getId())) {
                lastFail = new FailReason("TEACHER_UNAVAILABLE", "教师禁排时间限制");
                continue;
            }
            if (!checkTeacherDailyLimit(task.getTeacherId(), slot.getDayOfWeek(),
                    rules.teacherMaxDailySlots(), semesterId, refData.slotIdsByDay())) {
                lastFail = new FailReason("TEACHER_DAILY_LIMIT",
                        "教师每天最多" + rules.teacherMaxDailySlots() + "个大节");
                continue;
            }
            if (!checkClassDailyLimit(task.getClassId(), slot.getDayOfWeek(),
                    rules.classMaxDailySlots(), semesterId, refData.slotIdsByDay())) {
                lastFail = new FailReason("CLASS_DAILY_LIMIT",
                        "班级每天最多" + rules.classMaxDailySlots() + "个大节");
                continue;
            }
            if (!rules.allowSameCourseSameDay()
                    && usedDays.contains(slot.getDayOfWeek())
                    && hasSameCourseSameDay(task.getClassId(), task.getCourseId(),
                            slot.getDayOfWeek(), semesterId, refData.slotIdsByDay())) {
                lastFail = new FailReason("SAME_COURSE_SAME_DAY", "同一课程同一天不允许重复");
                continue;
            }

            for (Classroom room : matchedRooms) {
                String conflict = conflictService.checkConflict(task.getId(), slot.getId(), room.getId(), null);
                if (conflict == null) {
                    return AssignmentAttempt.placed(slot, room);
                }
                lastFail = new FailReason(
                        categorizeReason(conflict),
                        ScheduleConflictService.stripReasonTag(conflict).replace("排课失败:", ""));
            }
        }
        return AssignmentAttempt.notPlaced(lastFail);
    }

    private AutoScheduleResult finalizeBatch(
            AutoScheduleBatch batch, int totalTaskCount, ArrangeStats stats) {
        String status;
        String message;
        if (stats.failedTaskCount() == 0) {
            status = "SUCCESS";
            message = "自动排课完成，全部任务已安排";
        } else if (stats.successTaskCount() > 0) {
            status = "PARTIAL";
            message = "自动排课完成，部分任务未排满";
        } else {
            status = "FAILED";
            message = "自动排课完成，所有任务均未安排";
        }
        batchService.updateBatchResult(
                batch.getId(), stats.successTaskCount(), stats.failedTaskCount(),
                stats.generatedCount(), status, message);

        AutoScheduleResult result = new AutoScheduleResult();
        result.setBatchId(batch.getId());
        result.setBatchNo(batch.getBatchNo());
        result.setTotalTaskCount(totalTaskCount);
        result.setSuccessTaskCount(stats.successTaskCount());
        result.setFailedTaskCount(stats.failedTaskCount());
        result.setGeneratedScheduleCount(stats.generatedCount());
        result.setStatus(status);
        result.setMessage(message);
        return result;
    }

    // ========== 直接打 DB 的辅助方法（保持原样） ==========

    private int countScheduledSlots(Long taskId) {
        return scheduleMapper.selectCount(
                new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getTeachingTaskId, taskId)
                        .eq(Schedule::getDeleted, 0)).intValue();
    }

    /**
     * 这里用 &lt; maxSlots，而不是 &lt;= maxSlots。
     * 原因是当前正在尝试插入一个新大节，若已达到上限，则本次尝试必须拦下。
     *
     * 同一批次内"先插再查"的行在同事务（@Transactional 见类入口）下 MyBatis
     * 通过同一连接读得到（MySQL 的 read-own-writes），无需额外 batchId 过滤。
     */
    private boolean checkTeacherDailyLimit(Long teacherId, int dayOfWeek, int maxSlots,
                                           Long semesterId, java.util.Map<Integer, List<Long>> slotIdsByDay) {
        List<Long> slotIds = slotIdsByDay.getOrDefault(dayOfWeek, List.of());
        if (slotIds.isEmpty()) return true;
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getTeacherId, teacherId)
                .eq(Schedule::getSemesterId, semesterId)
                .eq(Schedule::getDeleted, 0)
                .in(Schedule::getTimeSlotId, slotIds);
        long count = scheduleMapper.selectCount(wrapper);
        return count < maxSlots;
    }

    private boolean checkClassDailyLimit(Long classId, int dayOfWeek, int maxSlots,
                                         Long semesterId, java.util.Map<Integer, List<Long>> slotIdsByDay) {
        List<Long> slotIds = slotIdsByDay.getOrDefault(dayOfWeek, List.of());
        if (slotIds.isEmpty()) return true;
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getClassId, classId)
                .eq(Schedule::getSemesterId, semesterId)
                .eq(Schedule::getDeleted, 0)
                .in(Schedule::getTimeSlotId, slotIds);
        long count = scheduleMapper.selectCount(wrapper);
        return count < maxSlots;
    }

    private boolean hasSameCourseSameDay(Long classId, Long courseId, int dayOfWeek,
                                         Long semesterId, java.util.Map<Integer, List<Long>> slotIdsByDay) {
        List<Long> slotIds = slotIdsByDay.getOrDefault(dayOfWeek, List.of());
        if (slotIds.isEmpty()) return false;
        long count = scheduleMapper.selectCount(
                new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getClassId, classId)
                        .eq(Schedule::getCourseId, courseId)
                        .eq(Schedule::getSemesterId, semesterId)
                        .eq(Schedule::getDeleted, 0)
                        .in(Schedule::getTimeSlotId, slotIds));
        return count > 0;
    }

    private void ensureSchedulesUnlocked(LambdaQueryWrapper<Schedule> wrapper) {
        List<Schedule> schedules = scheduleMapper.selectList(wrapper);
        for (Schedule schedule : schedules) {
            lockGuardService.ensureScheduleAndLinkedPlanUnlocked(schedule, "存在已锁定课程，不能清空当前排课结果");
        }
    }

    private void saveSchedule(TeachingTask task, TimeSlot slot, Classroom room, Long batchId) {
        Schedule schedule = new Schedule();
        schedule.setSemesterId(task.getSemesterId());
        schedule.setTeachingTaskId(task.getId());
        schedule.setCourseId(task.getCourseId());
        schedule.setTeacherId(task.getTeacherId());
        schedule.setClassId(task.getClassId());
        schedule.setTimeSlotId(slot.getId());
        schedule.setClassroomId(room.getId());
        schedule.setSourceType(ScheduleSourceType.AUTO.getCode());
        schedule.setBatchId(batchId);
        schedule.setDeleted(0);
        schedule.setCreateTime(LocalDateTime.now());
        schedule.setUpdateTime(LocalDateTime.now());
        scheduleMapper.insert(schedule);
    }

    private Long resolveSemesterId(AutoScheduleRequest request) {
        if (request.getSemesterId() != null) {
            return request.getSemesterId();
        }
        try {
            return semesterService.getCurrentSemester().getId();
        } catch (BusinessException e) {
            throw new BusinessException("未找到当前学期，无法执行自动排课");
        }
    }

    /**
     * 未排课记录需要稳定的失败类型编码，便于前端筛选和后续统计。
     * 冲突检测返回形如 [TYPE]message 的结构化标签，这里只读取标签，避免依赖中文文案。
     */
    private String categorizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "UNKNOWN";
        return ScheduleConflictService.extractReasonType(reason);
    }
}
