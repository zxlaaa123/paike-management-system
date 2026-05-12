package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleConflictService {

    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TeacherUnavailableTimeService unavailableTimeService;

    /**
     * 检查排课冲突。
     * @return null 表示无冲突，否则返回冲突描述信息
     */
    public String checkConflict(Long taskId, Long timeSlotId, Long classroomId, Long excludeScheduleId) {
        TeachingTask task = teachingTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            return "所选教学任务不存在";
        }
        TimeSlot timeSlot = timeSlotMapper.selectById(timeSlotId);
        if (timeSlot == null) {
            return "所选时间段不存在";
        }
        Classroom classroom = classroomMapper.selectById(classroomId);
        if (classroom == null || classroom.getDeleted() == 1) {
            return "所选教室不存在";
        }

        Course course = courseMapper.selectById(task.getCourseId());
        Teacher teacher = teacherMapper.selectById(task.getTeacherId());
        ClassInfo classInfo = classInfoMapper.selectById(task.getClassId());

        // 1. 停用教师不能参与排课
        if (teacher != null && teacher.getStatus() != 1) {
            return "排课失败：" + teacher.getName() + "老师已停用，不能参与排课";
        }
        // 1.5 教师禁排时间检查
        if (unavailableTimeService.isUnavailable(task.getTeacherId(), timeSlotId)) {
            return "排课失败：" + teacher.getName() + "老师在" + timeSlot.getTimeLabel() + "设置了禁排时间";
        }
        // 2. 停用班级不能参与排课
        if (classInfo != null && classInfo.getStatus() != 1) {
            return "排课失败：" + classInfo.getClassName() + "已停用，不能参与排课";
        }
        // 3. 停用教室不能参与排课
        if (classroom.getStatus() != 1) {
            return "排课失败：" + classroom.getRoomName() + "教室已停用，不能参与排课";
        }

        // 4. 班级人数不能大于教室容量
        if (classInfo != null && classroom.getCapacity() < classInfo.getStudentCount()) {
            return "排课失败：" + classInfo.getClassName() + "人数为" + classInfo.getStudentCount() + "，当前教室容量为" + classroom.getCapacity();
        }

        // 5. 实验课必须安排在实验室
        if (course != null && "EXPERIMENT".equals(course.getCourseType()) && !"LAB".equals(classroom.getRoomType())) {
            return "排课失败：实验课必须安排在实验室";
        }
        // 6. 机房课必须安排在机房
        if (course != null && "COMPUTER".equals(course.getCourseType()) && !"COMPUTER".equals(classroom.getRoomType())) {
            return "排课失败：机房课必须安排在机房";
        }

        // 构建排除当前记录的查询条件（编辑时排除自身）
        LambdaQueryWrapper<Schedule> baseWrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDeleted, 0);
        if (excludeScheduleId != null) {
            baseWrapper.ne(Schedule::getId, excludeScheduleId);
        }

        List<Schedule> existingSchedules = scheduleMapper.selectList(baseWrapper);

        String timeLabel = timeSlot.getTimeLabel();
        String teacherName = teacher != null ? teacher.getName() : "";
        String className = classInfo != null ? classInfo.getClassName() : "";
        Long teacherId = task.getTeacherId();
        Long classId = task.getClassId();

        for (Schedule s : existingSchedules) {
            // 只检查同一时间段的冲突
            if (!s.getTimeSlotId().equals(timeSlotId)) continue;

            // 7. 同一教师同一时间不能有两门课
            TeachingTask existingTask = teachingTaskMapper.selectById(s.getTeachingTaskId());
            if (existingTask != null && existingTask.getTeacherId().equals(teacherId)) {
                return "排课失败：" + teacherName + "老师在" + timeLabel + "已有课程";
            }

            // 8. 同一班级同一时间不能有两门课
            if (existingTask != null && existingTask.getClassId().equals(classId)) {
                return "排课失败：" + className + "在" + timeLabel + "已有课程";
            }

            // 9. 同一教室同一时间不能安排两门课
            if (s.getClassroomId().equals(classroomId)) {
                return "排课失败：" + classroom.getRoomName() + "教室在" + timeLabel + "已被占用";
            }
        }

        // 10. 教学任务不能超过每周课时
        // 统计该任务已排的大节数
        LambdaQueryWrapper<Schedule> taskWrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getTeachingTaskId, taskId)
                .eq(Schedule::getDeleted, 0);
        if (excludeScheduleId != null) {
            taskWrapper.ne(Schedule::getId, excludeScheduleId);
        }
        int scheduledSlots = scheduleMapper.selectCount(taskWrapper).intValue();
        int requiredSlots = (int) Math.ceil(task.getWeeklyHours() / 2.0);
        if (scheduledSlots + 1 > requiredSlots) {
            return "排课失败：该教学任务每周课时为" + task.getWeeklyHours() + "学时，最多排" + requiredSlots + "个大节，当前已排" + scheduledSlots + "个大节";
        }

        return null; // 无冲突
    }
}
