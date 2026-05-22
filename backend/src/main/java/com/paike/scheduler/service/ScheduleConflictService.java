package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
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
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
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
        TeachingTask task = teachingTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            return tagReason("TASK_NOT_FOUND", "所选教学任务不存在");
        }
        TimeSlot timeSlot = timeSlotMapper.selectById(timeSlotId);
        if (timeSlot == null) {
            return tagReason("TIME_SLOT_NOT_FOUND", "所选时间段不存在");
        }
        Classroom classroom = classroomMapper.selectById(classroomId);
        if (classroom == null || classroom.getDeleted() == 1) {
            return tagReason("CLASSROOM_NOT_FOUND", "所选教室不存在");
        }

        Course course = courseMapper.selectById(task.getCourseId());
        Teacher teacher = teacherMapper.selectById(task.getTeacherId());
        ClassInfo classInfo = classInfoMapper.selectById(task.getClassId());

        // 1. 停用教师不能参与排课
        if (teacher != null && teacher.getStatus() != 1) {
            return tagReason("TEACHER_DISABLED", "排课失败:" + teacher.getName() + "老师已停用,不能参与排课");
        }
        // 1.5 教师禁排时间检查（teacher 可能因被软删而为 null，需要兜底显示名）
        if (unavailableTimeService.isUnavailable(task.getTeacherId(), timeSlotId)) {
            String displayName = teacher != null ? teacher.getName() + "老师" : "该教师";
            return tagReason("TEACHER_UNAVAILABLE", "排课失败:" + displayName + "在" + timeSlot.getTimeLabel() + "设置了禁排时间");
        }
        // 2. 停用班级不能参与排课
        if (classInfo != null && classInfo.getStatus() != 1) {
            return tagReason("CLASS_DISABLED", "排课失败:" + classInfo.getClassName() + "已停用,不能参与排课");
        }
        // 3. 停用教室不能参与排课
        if (classroom.getStatus() != 1) {
            return tagReason("CLASSROOM_DISABLED", "排课失败:" + classroom.getRoomName() + "教室已停用,不能参与排课");
        }

        // 4. 班级人数不能大于教室容量
        if (classInfo != null && classroom.getCapacity() < classInfo.getStudentCount()) {
            return tagReason("CLASSROOM_CAPACITY_NOT_ENOUGH", "排课失败:" + classInfo.getClassName() + "人数为" + classInfo.getStudentCount() + ",当前教室容量为" + classroom.getCapacity());
        }

        // 5. 实验课必须安排在实验室
        if (course != null && CourseType.EXPERIMENT.getCode().equals(course.getCourseType())
                && !RoomType.LAB.getCode().equals(classroom.getRoomType())) {
            return tagReason("ROOM_TYPE_MISMATCH", "排课失败:实验课必须安排在实验室");
        }
        // 6. 机房课必须安排在机房
        if (course != null && CourseType.COMPUTER.getCode().equals(course.getCourseType())
                && !RoomType.COMPUTER.getCode().equals(classroom.getRoomType())) {
            return tagReason("ROOM_TYPE_MISMATCH", "排课失败:机房课必须安排在机房");
        }

        // 只查询同一时间段的排课记录,避免全表扫描
        LambdaQueryWrapper<Schedule> baseWrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDeleted, 0)
                .eq(Schedule::getTimeSlotId, timeSlotId)
                .eq(task.getSemesterId() != null, Schedule::getSemesterId, task.getSemesterId())
                .isNull(Schedule::getPlanId);
        if (excludeScheduleId != null) {
            baseWrapper.ne(Schedule::getId, excludeScheduleId);
        }

        List<Schedule> existingSchedules = scheduleMapper.selectList(baseWrapper);

        String timeLabel = timeSlot.getTimeLabel();
        String teacherName = teacher != null ? teacher.getName() : "";
        String className = classInfo != null ? classInfo.getClassName() : "";
        Long teacherId = task.getTeacherId();
        Long classId = task.getClassId();

        // 批量查询所有关联的教学任务,避免 N+1 查询
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

            // 7. 同一教师同一时间不能有两门课
            if (existingTask != null && existingTask.getTeacherId().equals(teacherId)) {
                return tagReason("TEACHER_CONFLICT", "排课失败:" + teacherName + "老师在" + timeLabel + "已有课程");
            }

            // 8. 同一班级同一时间不能有两门课
            if (existingTask != null && existingTask.getClassId().equals(classId)) {
                return tagReason("CLASS_CONFLICT", "排课失败:" + className + "在" + timeLabel + "已有课程");
            }

            // 9. 同一教室同一时间不能安排两门课
            if (s.getClassroomId().equals(classroomId)) {
                return tagReason("ROOM_CONFLICT", "排课失败:" + classroom.getRoomName() + "教室在" + timeLabel + "已被占用");
            }
        }

        // 10. 教学任务不能超过每周课时
        // 统计该任务已排的大节数
        LambdaQueryWrapper<Schedule> taskWrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getTeachingTaskId, taskId)
                .eq(Schedule::getDeleted, 0)
                .eq(task.getSemesterId() != null, Schedule::getSemesterId, task.getSemesterId())
                .isNull(Schedule::getPlanId);
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

        // 11. 读取软规则。前面的资源占用、容量、类型属于硬约束,这里的每日上限和同课同日属于配置化约束。
        int teacherMaxDailySlots = ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS");
        int classMaxDailySlots = ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS");
        boolean allowSameCourseSameDay = ruleService.getBoolValue("ALLOW_SAME_COURSE_SAME_DAY");

        // 预加载当天所有时间段 ID,供三条软规则复用
        List<Long> daySlotIds = timeSlotMapper.selectList(
                new LambdaQueryWrapper<TimeSlot>()
                        .eq(TimeSlot::getDayOfWeek, timeSlot.getDayOfWeek()))
                .stream().map(TimeSlot::getId).collect(Collectors.toList());

        // 批量统计每日冲突计数,一次查询替代之前的三次 selectCount
        Map<String, Long> dailyCounts = scheduleMapper.selectDailyConflictCounts(
                teacherId, classId, task.getCourseId(), daySlotIds, task.getSemesterId(), null, excludeScheduleId);
        long teacherDailyCount = dailyCounts.getOrDefault("teacherDaily", 0L);
        long classDailyCount = dailyCounts.getOrDefault("classDaily", 0L);
        long sameCourseCount = dailyCounts.getOrDefault("sameCourse", 0L);

        if (teacherMaxDailySlots > 0) {
            // 这里用 >=,因为当前待插入的大节尚未入库；一旦已达到上限,本次排课就必须拒绝。
            if (teacherDailyCount >= teacherMaxDailySlots) {
                return tagReason("TEACHER_DAILY_LIMIT", "排课失败:" + teacherName + "老师每天最多" + teacherMaxDailySlots + "个大节,当前已排" + teacherDailyCount + "个");
            }
        }

        if (classMaxDailySlots > 0) {
            if (classDailyCount >= classMaxDailySlots) {
                return tagReason("CLASS_DAILY_LIMIT", "排课失败:" + className + "每天最多" + classMaxDailySlots + "个大节,当前已排" + classDailyCount + "个");
            }
        }

        if (!allowSameCourseSameDay) {
            // 这里约束的是"同一班级 + 同一课程 + 同一天",不是简单按教师或时间段去重。
            if (sameCourseCount > 0) {
                return tagReason("SAME_COURSE_SAME_DAY", "排课失败:同一课程同一天不允许重复");
            }
        }

        return null; // 无冲突
    }
}
