package com.paike.scheduler.service;

import com.paike.scheduler.common.enums.ScheduleSourceType;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.service.dto.AutoScheduleRequest;
import com.paike.scheduler.service.dto.AutoScheduleResult;
import com.paike.scheduler.service.scheduling.SchedulingReferenceData;
import com.paike.scheduler.service.scheduling.SchedulingReferenceLoader;
import com.paike.scheduler.service.scheduling.SchedulingSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoScheduleService {

    /**
     * 自动排课主流程。
     * 核心思路是先把“更难排的任务”放前面，再按规则优先级尝试时间段和教室。
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
        // 1. 清空旧排课（如需要）
        if (request.isClearAllSchedule()) {
            ensureSchedulesUnlocked(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getDeleted, 0)
                    .eq(Schedule::getSemesterId, semesterId));
            scheduleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getDeleted, 0)
                    .eq(Schedule::getSemesterId, semesterId));
            unscheduledTaskService.clearBySemester(semesterId);
        } else if (request.isClearOldAutoSchedule()) {
            ensureSchedulesUnlocked(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getSourceType, ScheduleSourceType.AUTO.getCode())
                    .eq(Schedule::getDeleted, 0)
                    .eq(Schedule::getSemesterId, semesterId));
            scheduleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getSourceType, ScheduleSourceType.AUTO.getCode())
                    .eq(Schedule::getDeleted, 0)
                    .eq(Schedule::getSemesterId, semesterId));
            unscheduledTaskService.clearBySemester(semesterId);
        }

        // 2. 读取待排教学任务
        List<TeachingTask> allTasks = teachingTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeachingTask>()
                        .eq(TeachingTask::getDeleted, 0)
                        .eq(TeachingTask::getStatus, 1)
                        .eq(TeachingTask::getSemesterId, semesterId));

        List<TeachingTask> targetTasks;
        if (request.getTaskIds() != null && !request.getTaskIds().isEmpty()) {
            targetTasks = allTasks.stream()
                    .filter(t -> request.getTaskIds().contains(t.getId()))
                    .collect(Collectors.toList());
        } else {
            targetTasks = allTasks;
        }

        // 3. 创建批次
        AutoScheduleBatch batch = batchService.createBatch(targetTasks.size(), request.isClearOldAutoSchedule());

        // 4. 读取规则阈值（共享数据加载在第 5 步统一处理）
        int teacherMaxDailySlots = ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS");
        int classMaxDailySlots = ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS");
        boolean allowSameCourseSameDay = ruleService.getBoolValue("ALLOW_SAME_COURSE_SAME_DAY");

        // 5. 一次性加载所有共享读型数据（时间段已按晨课/避周五偏好排序）
        SchedulingReferenceData refData = referenceLoader.loadForAutoSchedule();
        List<TimeSlot> timeSlots = refData.sortedTimeSlots();
        Map<Integer, List<Long>> slotIdsByDay = refData.slotIdsByDay();
        List<Classroom> classrooms = refData.classrooms();
        Set<String> unavailableKeySet = refData.unavailableKeySet();
        Map<Long, Course> courseMap = refData.courseMap();
        Map<Long, ClassInfo> classMap = refData.classMap();

        // 6. 对教学任务排序（难排优先）
        targetTasks = SchedulingSupport.sortTasks(
                targetTasks,
                refData.unavailableCountByTeacher(),
                courseMap,
                classMap);

        // 9. 遍历排课
        int generatedCount = 0;
        int successTaskCount = 0;
        int failedTaskCount = 0;

        for (TeachingTask task : targetTasks) {
            // 计算需要排的大节数（weeklyHours 列允许 NULL，需做防御性兜底）
            Integer weekly = task.getWeeklyHours();
            int requiredSlots = weekly == null ? 0 : (int) Math.ceil(weekly / 2.0);
            int scheduledSlots = countScheduledSlots(task.getId());
            int remainingSlots = requiredSlots - scheduledSlots;

            if (remainingSlots <= 0) {
                successTaskCount++;
                continue;
            }

            // 预过滤：符合课程类型+容量+停用的教室
            Course course = courseMap.get(task.getCourseId());
            if (course == null) {
                unscheduledTaskService.addUnscheduledTask(batch.getId(), semesterId, task.getId(), requiredSlots,
                        scheduledSlots, remainingSlots, "COURSE_NOT_FOUND", "关联课程不存在或已删除");
                failedTaskCount++;
                continue;
            }
            ClassInfo classInfo = classMap.get(task.getClassId());
            if (classInfo == null) {
                unscheduledTaskService.addUnscheduledTask(batch.getId(), semesterId, task.getId(), requiredSlots,
                        scheduledSlots, remainingSlots, "CLASS_NOT_FOUND", "关联班级不存在或已删除");
                failedTaskCount++;
                continue;
            }
            String courseType = course.getCourseType();
            int studentCount = classInfo.getStudentCount();
            List<Classroom> matchedRooms = classrooms.stream()
                    .filter(r -> r.getCapacity() >= studentCount)
                    .filter(r -> SchedulingSupport.isRoomTypeMatched(courseType, r.getRoomType()))
                    .sorted(Comparator.comparingInt(Classroom::getCapacity))
                    .collect(Collectors.toList());

            if (matchedRooms.isEmpty()) {
                unscheduledTaskService.addUnscheduledTask(batch.getId(), semesterId, task.getId(), requiredSlots,
                        scheduledSlots, remainingSlots, "NO_MATCHED_CLASSROOM", "没有符合课程类型和容量要求的教室");
                failedTaskCount++;
                continue;
            }

            int currentSuccess = 0;
            String lastFailReason = "";
            String lastFailReasonType = "UNKNOWN";
            Set<Integer> usedDays = new HashSet<>();

            for (int i = 0; i < remainingSlots; i++) {
                boolean arranged = false;

                for (TimeSlot slot : timeSlots) {
                    // 跳过教师禁排时间
                    if (unavailableKeySet.contains(task.getTeacherId() + "_" + slot.getId())) {
                        lastFailReason = "教师禁排时间限制";
                        lastFailReasonType = "TEACHER_UNAVAILABLE";
                        continue;
                    }

                    // 检查教师每日最大课程数
                    if (!checkTeacherDailyLimit(task.getTeacherId(), slot.getDayOfWeek(), teacherMaxDailySlots, semesterId, slotIdsByDay)) {
                        lastFailReason = "教师每天最多" + teacherMaxDailySlots + "个大节";
                        lastFailReasonType = "TEACHER_DAILY_LIMIT";
                        continue;
                    }

                    // 检查班级每日最大课程数
                    if (!checkClassDailyLimit(task.getClassId(), slot.getDayOfWeek(), classMaxDailySlots, semesterId, slotIdsByDay)) {
                        lastFailReason = "班级每天最多" + classMaxDailySlots + "个大节";
                        lastFailReasonType = "CLASS_DAILY_LIMIT";
                        continue;
                    }

                    // 检查同一课程同一天重复
                    if (!allowSameCourseSameDay && usedDays.contains(slot.getDayOfWeek())) {
                        // 先判断本次 run 中是否已经给当前任务占过这一天，再回库里确认历史排课是否也已占用。
                        if (hasSameCourseSameDay(task.getClassId(), task.getCourseId(), slot.getDayOfWeek(), batch.getId(), semesterId, slotIdsByDay)) {
                            lastFailReason = "同一课程同一天不允许重复";
                            lastFailReasonType = "SAME_COURSE_SAME_DAY";
                            continue;
                        }
                    }

                    for (Classroom room : matchedRooms) {
                        // 复用冲突检测
                        String conflict = conflictService.checkConflict(task.getId(), slot.getId(), room.getId(), null);
                        if (conflict == null) {
                            // 无冲突，保存排课记录
                            saveSchedule(task, slot, room, batch.getId());
                            generatedCount++;
                            currentSuccess++;
                            arranged = true;
                            usedDays.add(slot.getDayOfWeek());
                            break;
                        } else {
                            lastFailReasonType = categorizeReason(conflict);
                            lastFailReason = ScheduleConflictService.stripReasonTag(conflict).replace("排课失败:", "");
                        }
                    }

                    if (arranged) break;
                }

                if (!arranged) {
                    break;
                }
            }

            if (currentSuccess >= remainingSlots) {
                successTaskCount++;
            } else {
                failedTaskCount++;
                unscheduledTaskService.addUnscheduledTask(batch.getId(), semesterId, task.getId(), requiredSlots,
                        scheduledSlots + currentSuccess, remainingSlots - currentSuccess,
                        lastFailReasonType, lastFailReason);
            }
        }

        // 10. 更新批次状态
        String status;
        String message;
        if (failedTaskCount == 0) {
            status = "SUCCESS";
            message = "自动排课完成，全部任务已安排";
        } else if (successTaskCount > 0) {
            status = "PARTIAL";
            message = "自动排课完成，部分任务未排满";
        } else {
            status = "FAILED";
            message = "自动排课完成，所有任务均未安排";
        }

        batchService.updateBatchResult(batch.getId(), successTaskCount, failedTaskCount, generatedCount, status, message);

        // 11. 构建返回结果
        AutoScheduleResult result = new AutoScheduleResult();
        result.setBatchId(batch.getId());
        result.setBatchNo(batch.getBatchNo());
        result.setTotalTaskCount(targetTasks.size());
        result.setSuccessTaskCount(successTaskCount);
        result.setFailedTaskCount(failedTaskCount);
        result.setGeneratedScheduleCount(generatedCount);
        result.setStatus(status);
        result.setMessage(message);
        return result;
    }

    // ========== 辅助方法 ==========

    private int countScheduledSlots(Long taskId) {
        return scheduleMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getTeachingTaskId, taskId)
                        .eq(Schedule::getDeleted, 0)).intValue();
    }

    /**
     * 这里用 < maxSlots，而不是 <= maxSlots。
     * 原因是当前正在尝试插入一个新大节，若已达到上限，则本次尝试必须拦下。
     *
     * 同一批次内"先插再查"的行在同事务（@Transactional 见类入口）下 MyBatis
     * 通过同一连接读得到（MySQL 的 read-own-writes），无需额外 batchId 过滤。
     * 之前残留的 currentBatchId 参数已删除，避免给调用方造成"还在生效"的错觉。
     */
    private boolean checkTeacherDailyLimit(Long teacherId, int dayOfWeek, int maxSlots, Long semesterId, Map<Integer, List<Long>> slotIdsByDay) {
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

    /**
     * 班级每日上限和教师上限同口径处理，避免新增当前大节后越过规则阈值。
     */
    private boolean checkClassDailyLimit(Long classId, int dayOfWeek, int maxSlots, Long semesterId, Map<Integer, List<Long>> slotIdsByDay) {
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

    private boolean hasSameCourseSameDay(Long classId, Long courseId, int dayOfWeek, Long batchId, Long semesterId, Map<Integer, List<Long>> slotIdsByDay) {
        List<Long> slotIds = slotIdsByDay.getOrDefault(dayOfWeek, List.of());
        if (slotIds.isEmpty()) return false;
        long count = scheduleMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Schedule>()
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
     * 这里把冲突检测返回的中文原因归一化为枚举风格字符串。
     */
    private String categorizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "UNKNOWN";
        String taggedType = ScheduleConflictService.extractReasonType(reason);
        if (!"UNKNOWN".equals(taggedType)) return taggedType;
        if (reason.contains("教师禁排")) return "TEACHER_UNAVAILABLE";
        if (reason.contains("已有课程") && reason.contains("老师")) return "TEACHER_CONFLICT";
        if (reason.contains("已有课程") && !reason.contains("老师")) return "CLASS_CONFLICT";
        if (reason.contains("教室") && reason.contains("占用")) return "ROOM_CONFLICT";
        if (reason.contains("容量")) return "CLASSROOM_CAPACITY_NOT_ENOUGH";
        if (reason.contains("实验课")) return "ROOM_TYPE_MISMATCH";
        if (reason.contains("机房课")) return "ROOM_TYPE_MISMATCH";
        if (reason.contains("每周课时")) return "TASK_NOT_FULLY_SCHEDULED";
        if (reason.contains("教师每天")) return "TEACHER_DAILY_LIMIT";
        if (reason.contains("班级每天")) return "CLASS_DAILY_LIMIT";
        if (reason.contains("同一课程同一天")) return "SAME_COURSE_SAME_DAY";
        if (reason.contains("没有符合")) return "NO_MATCHED_CLASSROOM";
        return "UNKNOWN";
    }

}
