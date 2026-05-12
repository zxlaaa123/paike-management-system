package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;

    @GetMapping
    public Result<Page<Schedule>> list(
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) String teacherName,
        @RequestParam(required = false) String className,
        @RequestParam(required = false) String roomName,
        @RequestParam(required = false) Integer dayOfWeek,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
            .eq(Schedule::getDeleted, 0);
        wrapper.orderByDesc(Schedule::getCreateTime);
        Page<Schedule> result = scheduleMapper.selectPage(new Page<>(page, size), wrapper);
        fillRelations(result.getRecords());

        // 内存过滤
        List<Schedule> filtered = result.getRecords().stream().filter(s -> {
            if (courseName != null && !courseName.isBlank()) {
                if (s.getCourseName() == null || !s.getCourseName().contains(courseName)) return false;
            }
            if (teacherName != null && !teacherName.isBlank()) {
                if (s.getTeacherName() == null || !s.getTeacherName().contains(teacherName)) return false;
            }
            if (className != null && !className.isBlank()) {
                if (s.getClassName() == null || !s.getClassName().contains(className)) return false;
            }
            if (roomName != null && !roomName.isBlank()) {
                if (s.getRoomName() == null || !s.getRoomName().contains(roomName)) return false;
            }
            if (dayOfWeek != null) {
                if (s.getDayOfWeek() == null || !s.getDayOfWeek().equals(dayOfWeek)) return false;
            }
            return true;
        }).collect(Collectors.toList());

        Page<Schedule> pageResult = new Page<>(page, size);
        pageResult.setRecords(filtered);
        pageResult.setTotal(filtered.size());
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<Schedule> getById(@PathVariable Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null || schedule.getDeleted() == 1) {
            return Result.fail(404, "排课记录不存在");
        }
        fillRelation(schedule);
        return Result.success(schedule);
    }

    @PostMapping
    public Result<Schedule> create(@Valid @RequestBody ScheduleForm form) {
        // 校验教学任务是否存在
        TeachingTask task = teachingTaskMapper.selectById(form.getTeachingTaskId());
        if (task == null || task.getDeleted() == 1) {
            return Result.fail(400, "所选教学任务不存在");
        }
        // 校验时间段是否存在
        TimeSlot timeSlot = timeSlotMapper.selectById(form.getTimeSlotId());
        if (timeSlot == null) {
            return Result.fail(400, "所选时间段不存在");
        }
        // 校验教室是否存在
        Classroom classroom = classroomMapper.selectById(form.getClassroomId());
        if (classroom == null || classroom.getDeleted() == 1) {
            return Result.fail(400, "所选教室不存在");
        }

        Schedule schedule = new Schedule();
        schedule.setTeachingTaskId(form.getTeachingTaskId());
        schedule.setTimeSlotId(form.getTimeSlotId());
        schedule.setClassroomId(form.getClassroomId());
        schedule.setDeleted(0);
        schedule.setCreateTime(LocalDateTime.now());
        schedule.setUpdateTime(LocalDateTime.now());
        scheduleMapper.insert(schedule);

        fillRelation(schedule);
        return Result.success(schedule);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null || schedule.getDeleted() == 1) {
            return Result.fail(404, "排课记录不存在");
        }
        schedule.setDeleted(1);
        schedule.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(schedule);
        return Result.success("删除成功", null);
    }

    /** 按班级查询排课列表 */
    @GetMapping("/class/{classId}")
    public Result<List<Schedule>> listByClass(@PathVariable Long classId) {
        // 先查该班级的所有教学任务
        List<TeachingTask> tasks = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getClassId, classId)
                .eq(TeachingTask::getDeleted, 0)
        );
        if (tasks.isEmpty()) {
            return Result.success(List.of());
        }
        List<Long> taskIds = tasks.stream().map(TeachingTask::getId).collect(Collectors.toList());
        List<Schedule> list = scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .in(Schedule::getTeachingTaskId, taskIds)
                .eq(Schedule::getDeleted, 0)
        );
        fillRelations(list);
        return Result.success(list);
    }

    /** 按教师查询排课列表 */
    @GetMapping("/teacher/{teacherId}")
    public Result<List<Schedule>> listByTeacher(@PathVariable Long teacherId) {
        List<TeachingTask> tasks = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getTeacherId, teacherId)
                .eq(TeachingTask::getDeleted, 0)
        );
        if (tasks.isEmpty()) {
            return Result.success(List.of());
        }
        List<Long> taskIds = tasks.stream().map(TeachingTask::getId).collect(Collectors.toList());
        List<Schedule> list = scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .in(Schedule::getTeachingTaskId, taskIds)
                .eq(Schedule::getDeleted, 0)
        );
        fillRelations(list);
        return Result.success(list);
    }

    /** 按教室查询排课列表 */
    @GetMapping("/classroom/{classroomId}")
    public Result<List<Schedule>> listByClassroom(@PathVariable Long classroomId) {
        List<Schedule> list = scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getClassroomId, classroomId)
                .eq(Schedule::getDeleted, 0)
        );
        fillRelations(list);
        return Result.success(list);
    }

    private void fillRelations(List<Schedule> list) {
        if (list.isEmpty()) return;
        for (Schedule s : list) {
            fillRelation(s);
        }
    }

    private void fillRelation(Schedule s) {
        // 时间段
        TimeSlot timeSlot = timeSlotMapper.selectById(s.getTimeSlotId());
        if (timeSlot != null) {
            s.setTimeLabel(timeSlot.getTimeLabel());
            s.setDayOfWeek(timeSlot.getDayOfWeek());
            s.setPeriodNo(timeSlot.getPeriodNo());
        }
        // 教室
        Classroom classroom = classroomMapper.selectById(s.getClassroomId());
        if (classroom != null) {
            s.setRoomName(classroom.getRoomName());
            s.setBuilding(classroom.getBuilding());
        }
        // 教学任务 → 课程/教师/班级
        TeachingTask task = teachingTaskMapper.selectById(s.getTeachingTaskId());
        if (task != null) {
            Course course = courseMapper.selectById(task.getCourseId());
            if (course != null) s.setCourseName(course.getCourseName());
            Teacher teacher = teacherMapper.selectById(task.getTeacherId());
            if (teacher != null) s.setTeacherName(teacher.getName());
            ClassInfo classInfo = classInfoMapper.selectById(task.getClassId());
            if (classInfo != null) s.setClassName(classInfo.getClassName());
        }
    }

    @Data
    public static class ScheduleForm {
        @NotNull(message = "教学任务不能为空")
        private Long teachingTaskId;
        @NotNull(message = "时间段不能为空")
        private Long timeSlotId;
        @NotNull(message = "教室不能为空")
        private Long classroomId;
    }
}
