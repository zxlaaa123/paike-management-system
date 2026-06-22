package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.vo.TeachingTaskVo;
import com.paike.scheduler.service.dto.ScheduleDailyConflictCounts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleConflictService {

    /**
     * 手动排课和自动排课共用的冲突检测入口。
     * 返回 null 表示允许排入,返回字符串表示第一个命中的阻塞原因。
     */
    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final ClassroomMapper classroomMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TeacherUnavailableTimeService unavailableTimeService;
    private final ScheduleRuleService ruleService;

    public static String tagReason(String type, String message) {
        return "[" + type + "]" + message;
    }

    public static String extractReasonType(String reason) {
        if (reason == null || !reason.startsWith("[")) return "UNKNOWN";
        int end = reason.indexOf(']');
        if (end <= 1) return "UNKNOWN";
        return reason.substring(1, end);
    }

    public static String stripReasonTag(String reason) {
        if (reason == null || !reason.startsWith("[")) return reason;
        int end = reason.indexOf(']');
        return end > 0 ? reason.substring(end + 1) : reason;
    }

    /**
     * 检查排课冲突。
     * @return null 表示无冲突,否则返回冲突描述信息
     */
    public String checkConflict(Long taskId, Long timeSlotId, Long classroomId, Long excludeScheduleId) {
        TeachingTaskVo task = teachingTaskMapper.selectConflictCheckById(taskId);
        if (task == null) {
            return tagReason("TASK_NOT_FOUND", "所选教学任务不存在");
        }
        TimeSlot timeSlot = timeSlotMapper.selectById(timeSlotId);
        if (timeSlot == null) {
            return tagReason("TIME_SLOT_NOT_FOUND", "所选时间段不存在");
        }
        Classroom classroom = classroomMapper.selectById(classroomId);
        if (classroom == null || Integer.valueOf(1).equals(classroom.getDeleted())) {
            return tagReason("CLASSROOM_NOT_FOUND", "所选教室不存在");
        }

        String basicViolation = checkBasicConstraints(task, timeSlot, classroom);
        if (basicViolation != null) {
            return basicViolation;
        }

        String resourceViolation = checkResourceConflicts(task, timeSlot, classroom, timeSlotId, excludeScheduleId);
        if (resourceViolation != null) {
            return resourceViolation;
        }

        String weeklyViolation = checkWeeklyHourLimit(task, taskId, excludeScheduleId);
        if (weeklyViolation != null) {
            return weeklyViolation;
        }

        String softRuleViolation = checkSoftRules(task, timeSlot, excludeScheduleId);
        if (softRuleViolation != null) {
            return softRuleViolation;
        }

        return null; // 无冲突
    }

    /**
     * 基础硬约束：教师/班级/教室状态、教室容量、课程-教室类型匹配。
     */
    private String checkBasicConstraints(TeachingTaskVo task, TimeSlot timeSlot, Classroom classroom) {
        // 1. 停用教师不能参与排课
        if (task.getTeacherStatus() != null && !Integer.valueOf(1).equals(task.getTeacherStatus())) {
            return tagReason("TEACHER_DISABLED", "排课失败:" + task.getTeacherName() + "老师已停用,不能参与排课");
        }
        // 1.5 教师禁排时间检查
        if (unavailableTimeService.isUnavailable(task.getTeacherId(), timeSlot.getId())) {
            String displayName = task.getTeacherName() != null ? task.getTeacherName() + "老师" : "该教师";
            return tagReason("TEACHER_UNAVAILABLE", "排课失败:" + displayName + "在" + timeSlot.getTimeLabel() + "设置了禁排时间");
        }
        // 2. 停用班级不能参与排课
        if (task.getClassStatus() != null && !Integer.valueOf(1).equals(task.getClassStatus())) {
            return tagReason("CLASS_DISABLED", "排课失败:" + task.getClassName() + "已停用,不能参与排课");
        }
        // 3. 停用教室不能参与排课
        if (!Integer.valueOf(1).equals(classroom.getStatus())) {
            return tagReason("CLASSROOM_DISABLED", "排课失败:" + classroom.getRoomName() + "教室已停用,不能参与排课");
        }

        // 4. 班级人数不能大于教室容量
        if (task.getClassId() != null && task.getStudentCount() == null) {
            return tagReason("CLASSROOM_CAPACITY_NOT_ENOUGH", "排课失败:" + task.getClassName() + "人数未配置");
        }
        if (task.getClassId() != null && classroom.getCapacity() == null) {
            return tagReason("CLASSROOM_CAPACITY_NOT_ENOUGH", "排课失败:" + classroom.getRoomName() + "教室容量未配置");
        }
        if (task.getStudentCount() != null && classroom.getCapacity() != null && classroom.getCapacity() < task.getStudentCount()) {
            return tagReason("CLASSROOM_CAPACITY_NOT_ENOUGH", "排课失败:" + task.getClassName() + "人数为" + task.getStudentCount() + ",当前教室容量为" + classroom.getCapacity());
        }

        // 5. 实验课必须安排在实验室
        if (CourseType.EXPERIMENT.getCode().equals(task.getCourseType())
                && !RoomType.LAB.getCode().equals(classroom.getRoomType())) {
            return tagReason("ROOM_TYPE_MISMATCH", "排课失败:实验课必须安排在实验室");
        }
        // 6. 机房课必须安排在机房
        if (CourseType.COMPUTER.getCode().equals(task.getCourseType())
                && !RoomType.COMPUTER.getCode().equals(classroom.getRoomType())) {
            return tagReason("ROOM_TYPE_MISMATCH", "排课失败:机房课必须安排在机房");
        }
        return null;
    }

    /**
     * 资源冲突检测：教师/班级/教室在同一时间段的占用（V10 周段相交判定）。
     */
    private String checkResourceConflicts(TeachingTaskVo task, TimeSlot timeSlot, Classroom classroom,
                                           Long timeSlotId, Long excludeScheduleId) {
        LambdaQueryWrapper<Schedule> baseWrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getTimeSlotId, timeSlotId)
                .eq(task.getSemesterId() != null, Schedule::getSemesterId, task.getSemesterId());
        if (excludeScheduleId != null) {
            baseWrapper.ne(Schedule::getId, excludeScheduleId);
        }
        List<Schedule> existingSchedules = scheduleMapper.selectList(baseWrapper);

        String timeLabel = timeSlot.getTimeLabel();
        String teacherName = task.getTeacherName() != null ? task.getTeacherName() : "";
        String className = task.getClassName() != null ? task.getClassName() : "";
        Long teacherId = task.getTeacherId();
        Long classId = task.getClassId();
        String currentWeekType = task.getWeekType();
        Integer currentStartWeek = task.getStartWeek();
        Integer currentEndWeek = task.getEndWeek();

        // 批量查询关联教学任务，避免 N+1
        List<Long> existingTaskIds = existingSchedules.stream()
            .map(Schedule::getTeachingTaskId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, TeachingTask> existingTaskMap = existingTaskIds.isEmpty() ? Map.of() :
            teachingTaskMapper.selectBatchIds(existingTaskIds).stream()
                .collect(Collectors.toMap(TeachingTask::getId, java.util.function.Function.identity(), (a, b) -> a));

        for (Schedule s : existingSchedules) {
            TeachingTask existingTask = existingTaskMap.get(s.getTeachingTaskId());

            // 7. 同一教师同一时间不能有两门课（V10：实际自然周集合相交才冲突）
            if (existingTask != null && Objects.equals(existingTask.getTeacherId(), teacherId)
                    && WeekPatternSupport.overlap(currentWeekType, currentStartWeek, currentEndWeek,
                            existingTask.getWeekType(), existingTask.getStartWeek(), existingTask.getEndWeek())) {
                return tagReason("TEACHER_CONFLICT", "排课失败:" + teacherName + "老师在" + timeLabel + "已有课程");
            }

            // 8. 同一班级同一时间不能有两门课（V10 周段同上）
            if (existingTask != null && Objects.equals(existingTask.getClassId(), classId)
                    && WeekPatternSupport.overlap(currentWeekType, currentStartWeek, currentEndWeek,
                            existingTask.getWeekType(), existingTask.getStartWeek(), existingTask.getEndWeek())) {
                return tagReason("CLASS_CONFLICT", "排课失败:" + className + "在" + timeLabel + "已有课程");
            }

            // 9. 同一教室同一时间不能安排两门课（V10 周段）
            if (Objects.equals(s.getClassroomId(), classroom.getId())
                    && WeekPatternSupport.overlap(currentWeekType, currentStartWeek, currentEndWeek,
                            s.getWeekType(), s.getStartWeek(), s.getEndWeek())) {
                return tagReason("ROOM_CONFLICT", "排课失败:" + classroom.getRoomName() + "教室在" + timeLabel + "已被占用");
            }
        }
        return null;
    }

    /**
     * 每周课时上限检查。
     */
    private String checkWeeklyHourLimit(TeachingTaskVo task, Long taskId, Long excludeScheduleId) {
        LambdaQueryWrapper<Schedule> taskWrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getTeachingTaskId, taskId)
                .eq(task.getSemesterId() != null, Schedule::getSemesterId, task.getSemesterId());
        if (excludeScheduleId != null) {
            taskWrapper.ne(Schedule::getId, excludeScheduleId);
        }
        int scheduledSlots = scheduleMapper.selectCount(taskWrapper).intValue();
        Integer weeklyHours = task.getWeeklyHours();
        int weeklyHoursVal = weeklyHours == null ? 0 : weeklyHours;
        int requiredSlots = (int) Math.ceil(weeklyHoursVal / 2.0);
        if (scheduledSlots + 1 > requiredSlots) {
            return tagReason("TASK_NOT_FULLY_SCHEDULED", "排课失败:该教学任务每周课时为" + weeklyHoursVal + "学时,最多排" + requiredSlots + "个大节,当前已排" + scheduledSlots + "个大节");
        }
        return null;
    }

    /**
     * 软规则：教师每日上限、班级每日上限、同课同日限制。
     */
    private String checkSoftRules(TeachingTaskVo task, TimeSlot timeSlot, Long excludeScheduleId) {
        int teacherMaxDailySlots = ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS");
        int classMaxDailySlots = ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS");
        boolean allowSameCourseSameDay = ruleService.getBoolValue("ALLOW_SAME_COURSE_SAME_DAY");

        // 预加载当天所有时间段 ID
        List<Long> daySlotIds = timeSlotMapper.selectList(
                new LambdaQueryWrapper<TimeSlot>()
                        .eq(TimeSlot::getDayOfWeek, timeSlot.getDayOfWeek()))
                .stream().map(TimeSlot::getId).collect(Collectors.toList());

        // 批量统计每日冲突计数
        ScheduleDailyConflictCounts dailyCounts = scheduleMapper.selectDailyConflictCounts(
                task.getTeacherId(), task.getClassId(), task.getCourseId(), daySlotIds, task.getSemesterId(), null, excludeScheduleId,
                WeekTypeSupport.normalize(task.getWeekType()));
        long teacherDailyCount = dailyCounts == null ? 0L : dailyCounts.teacherDailyOrZero();
        long classDailyCount = dailyCounts == null ? 0L : dailyCounts.classDailyOrZero();
        long sameCourseCount = dailyCounts == null ? 0L : dailyCounts.sameCourseOrZero();

        String teacherName = task.getTeacherName() != null ? task.getTeacherName() : "";
        String className = task.getClassName() != null ? task.getClassName() : "";

        if (teacherMaxDailySlots > 0 && teacherDailyCount >= teacherMaxDailySlots) {
            return tagReason("TEACHER_DAILY_LIMIT", "排课失败:" + teacherName + "老师每天最多" + teacherMaxDailySlots + "个大节,当前已排" + teacherDailyCount + "个");
        }

        if (classMaxDailySlots > 0 && classDailyCount >= classMaxDailySlots) {
            return tagReason("CLASS_DAILY_LIMIT", "排课失败:" + className + "每天最多" + classMaxDailySlots + "个大节,当前已排" + classDailyCount + "个");
        }

        if (!allowSameCourseSameDay && sameCourseCount > 0) {
            return tagReason("SAME_COURSE_SAME_DAY", "排课失败:同一课程同一天不允许重复");
        }
        return null;
    }
}
