package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class TimetableController {

    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;

    /** 班级课表 */
    @GetMapping("/classes/{classId}")
    public Result<List<TimetableVo>> classTimetable(@PathVariable Long classId) {
        List<Schedule> schedules = queryByClassId(classId);
        return Result.success(toTimetableVos(schedules));
    }

    /** 教师课表 */
    @GetMapping("/teachers/{teacherId}")
    public Result<List<TimetableVo>> teacherTimetable(@PathVariable Long teacherId) {
        List<Schedule> schedules = queryByTeacherId(teacherId);
        return Result.success(toTimetableVos(schedules));
    }

    /** 教室课表 */
    @GetMapping("/classrooms/{classroomId}")
    public Result<List<TimetableVo>> classroomTimetable(@PathVariable Long classroomId) {
        List<Schedule> schedules = queryByClassroomId(classroomId);
        return Result.success(toTimetableVos(schedules));
    }

    private List<Schedule> queryByClassId(Long classId) {
        List<TeachingTask> tasks = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getClassId, classId)
                .eq(TeachingTask::getDeleted, 0)
        );
        if (tasks.isEmpty()) return List.of();
        List<Long> taskIds = tasks.stream().map(TeachingTask::getId).collect(Collectors.toList());
        return scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .in(Schedule::getTeachingTaskId, taskIds)
                .eq(Schedule::getDeleted, 0)
        );
    }

    private List<Schedule> queryByTeacherId(Long teacherId) {
        List<TeachingTask> tasks = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getTeacherId, teacherId)
                .eq(TeachingTask::getDeleted, 0)
        );
        if (tasks.isEmpty()) return List.of();
        List<Long> taskIds = tasks.stream().map(TeachingTask::getId).collect(Collectors.toList());
        return scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .in(Schedule::getTeachingTaskId, taskIds)
                .eq(Schedule::getDeleted, 0)
        );
    }

    private List<Schedule> queryByClassroomId(Long classroomId) {
        return scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getClassroomId, classroomId)
                .eq(Schedule::getDeleted, 0)
        );
    }

    private List<TimetableVo> toTimetableVos(List<Schedule> schedules) {
        return schedules.stream().map(s -> {
            TimetableVo vo = new TimetableVo();
            vo.setScheduleId(s.getId());

            TimeSlot timeSlot = timeSlotMapper.selectById(s.getTimeSlotId());
            if (timeSlot != null) {
                vo.setTimeSlotId(timeSlot.getId());
                vo.setDayOfWeek(timeSlot.getDayOfWeek());
                vo.setPeriod(timeSlot.getPeriodNo());
                vo.setTimeSlotName(timeSlot.getTimeLabel());
            }

            Classroom classroom = classroomMapper.selectById(s.getClassroomId());
            if (classroom != null) {
                vo.setClassroomName(classroom.getRoomName());
                vo.setBuilding(classroom.getBuilding());
            }

            TeachingTask task = teachingTaskMapper.selectById(s.getTeachingTaskId());
            if (task != null) {
                Course course = courseMapper.selectById(task.getCourseId());
                if (course != null) {
                    vo.setCourseName(course.getCourseName());
                    vo.setCourseType(course.getCourseType());
                }
                Teacher teacher = teacherMapper.selectById(task.getTeacherId());
                if (teacher != null) vo.setTeacherName(teacher.getName());
                ClassInfo classInfo = classInfoMapper.selectById(task.getClassId());
                if (classInfo != null) vo.setClassName(classInfo.getClassName());
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
