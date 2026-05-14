package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.enums.ScheduleSourceType;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.ScheduleConflictService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
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
    private final ScheduleConflictService conflictService;
    private final AutoScheduleBatchMapper autoScheduleBatchMapper;

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
        // 使用自定义 SQL 进行数据库层面过滤和分页
        Page<Schedule> pageResult = scheduleMapper.selectFilteredSchedulePage(
            courseName, teacherName, className, roomName, dayOfWeek,
            new Page<>(page, size));

        if (pageResult.getRecords().isEmpty()) {
            return Result.success(pageResult);
        }

        fillRelations(pageResult.getRecords());
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
        // 冲突检测
        String conflict = conflictService.checkConflict(
            form.getTeachingTaskId(), form.getTimeSlotId(), form.getClassroomId(), null);
        if (conflict != null) {
            return Result.fail(400, conflict);
        }

        TeachingTask task = teachingTaskMapper.selectById(form.getTeachingTaskId());

        Schedule schedule = new Schedule();
        schedule.setTeachingTaskId(form.getTeachingTaskId());
        schedule.setCourseId(task.getCourseId());
        schedule.setTeacherId(task.getTeacherId());
        schedule.setClassId(task.getClassId());
        schedule.setTimeSlotId(form.getTimeSlotId());
        schedule.setClassroomId(form.getClassroomId());
        schedule.setSourceType(ScheduleSourceType.MANUAL.getCode());
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
        scheduleMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    /** 按班级查询排课列表 */
    @GetMapping("/class/{classId}")
    public Result<List<Schedule>> listByClass(@PathVariable Long classId) {
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

    /** 冲突检测接口（前端预检用，保存时仍会再次检测） */
    @PostMapping("/check-conflict")
    public Result<Map<String, Object>> checkConflict(@Valid @RequestBody ScheduleForm form) {
        String conflict = conflictService.checkConflict(
            form.getTeachingTaskId(), form.getTimeSlotId(), form.getClassroomId(), null);
        if (conflict != null) {
            return Result.success(Map.of("hasConflict", true, "message", conflict));
        }
        return Result.success(Map.of("hasConflict", false, "message", ""));
    }

    private void fillRelations(List<Schedule> list) {
        if (list.isEmpty()) return;

        // 收集所有需要查询的ID
        List<Long> timeSlotIds = list.stream().map(Schedule::getTimeSlotId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> classroomIds = list.stream().map(Schedule::getClassroomId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> taskIds = list.stream().map(Schedule::getTeachingTaskId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> batchIds = list.stream().map(Schedule::getBatchId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // 批量查询关联数据
        Map<Long, TimeSlot> timeSlotMap = timeSlotIds.isEmpty() ? Map.of() :
            timeSlotMapper.selectBatchIds(timeSlotIds).stream().collect(Collectors.toMap(TimeSlot::getId, Function.identity(), (a, b) -> a));
        Map<Long, Classroom> classroomMap = classroomIds.isEmpty() ? Map.of() :
            classroomMapper.selectBatchIds(classroomIds).stream().collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));
        Map<Long, TeachingTask> taskMap = taskIds.isEmpty() ? Map.of() :
            teachingTaskMapper.selectBatchIds(taskIds).stream().collect(Collectors.toMap(TeachingTask::getId, Function.identity(), (a, b) -> a));
        Map<Long, AutoScheduleBatch> batchMap = batchIds.isEmpty() ? Map.of() :
            autoScheduleBatchMapper.selectBatchIds(batchIds).stream().collect(Collectors.toMap(AutoScheduleBatch::getId, Function.identity(), (a, b) -> a));

        // 收集教学任务关联的课程/教师/班级ID
        List<Long> courseIds = new ArrayList<>();
        List<Long> teacherIds = new ArrayList<>();
        List<Long> classIds = new ArrayList<>();
        for (TeachingTask task : taskMap.values()) {
            if (task.getCourseId() != null) courseIds.add(task.getCourseId());
            if (task.getTeacherId() != null) teacherIds.add(task.getTeacherId());
            if (task.getClassId() != null) classIds.add(task.getClassId());
        }

        Map<Long, Course> courseMap = courseIds.isEmpty() ? Map.of() :
            courseMapper.selectBatchIds(courseIds).stream().collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherIds.isEmpty() ? Map.of() :
            teacherMapper.selectBatchIds(teacherIds).stream().collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        Map<Long, ClassInfo> classMap = classIds.isEmpty() ? Map.of() :
            classInfoMapper.selectBatchIds(classIds).stream().collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));

        // 填充每条记录
        for (Schedule s : list) {
            TimeSlot timeSlot = timeSlotMap.get(s.getTimeSlotId());
            if (timeSlot != null) {
                s.setTimeLabel(timeSlot.getTimeLabel());
                s.setDayOfWeek(timeSlot.getDayOfWeek());
                s.setPeriodNo(timeSlot.getPeriodNo());
            }
            Classroom classroom = classroomMap.get(s.getClassroomId());
            if (classroom != null) {
                s.setRoomName(classroom.getRoomName());
                s.setBuilding(classroom.getBuilding());
            }
            TeachingTask task = taskMap.get(s.getTeachingTaskId());
            if (task != null) {
                Course course = courseMap.get(task.getCourseId());
                if (course != null) s.setCourseName(course.getCourseName());
                Teacher teacher = teacherMap.get(task.getTeacherId());
                if (teacher != null) s.setTeacherName(teacher.getName());
                ClassInfo classInfo = classMap.get(task.getClassId());
                if (classInfo != null) s.setClassName(classInfo.getClassName());
            }
            if (s.getSourceType() != null) {
                s.setSourceTypeName(ScheduleSourceType.AUTO.getCode().equals(s.getSourceType()) ? "自动排课" : "手动排课");
            } else {
                s.setSourceTypeName("手动排课");
            }
            if (s.getBatchId() != null) {
                AutoScheduleBatch batch = batchMap.get(s.getBatchId());
                if (batch != null) s.setBatchNo(batch.getBatchNo());
            }
        }
    }

    private void fillRelation(Schedule s) {
        // 批量查询所有关联数据（单条记录也走批量接口，统一逻辑）
        fillRelations(List.of(s));
    }

    @Getter
    public static class ScheduleForm {
        @NotNull(message = "教学任务不能为空")
        private Long teachingTaskId;
        @NotNull(message = "时间段不能为空")
        private Long timeSlotId;
        @NotNull(message = "教室不能为空")
        private Long classroomId;
    }
}
