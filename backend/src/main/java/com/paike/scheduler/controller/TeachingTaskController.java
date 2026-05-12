package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teaching-tasks")
@RequiredArgsConstructor
public class TeachingTaskController {

    private final TeachingTaskMapper teachingTaskMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;

    @GetMapping
    public Result<Page<TeachingTask>> list(
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) String teacherName,
        @RequestParam(required = false) String className,
        @RequestParam(required = false) Integer status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        LambdaQueryWrapper<TeachingTask> wrapper = new LambdaQueryWrapper<TeachingTask>()
            .eq(TeachingTask::getDeleted, 0);
        if (status != null) {
            wrapper.eq(TeachingTask::getStatus, status);
        }
        wrapper.orderByDesc(TeachingTask::getCreateTime);
        Page<TeachingTask> result = teachingTaskMapper.selectPage(new Page<>(page, size), wrapper);

        // 收集所有关联ID
        List<Long> courseIds = result.getRecords().stream().map(TeachingTask::getCourseId).distinct().collect(Collectors.toList());
        List<Long> teacherIds = result.getRecords().stream().map(TeachingTask::getTeacherId).distinct().collect(Collectors.toList());
        List<Long> classIds = result.getRecords().stream().map(TeachingTask::getClassId).distinct().collect(Collectors.toList());

        // 批量查询关联数据
        Map<Long, String> courseNameMap = courseIds.isEmpty() ? Map.of() :
            courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName));
        Map<Long, String> teacherNameMap = teacherIds.isEmpty() ? Map.of() :
            teacherMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Teacher::getName));
        Map<Long, String> classNameMap = classIds.isEmpty() ? Map.of() :
            classInfoMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassInfo::getId, ClassInfo::getClassName));

        // 内存过滤（课程名、教师名、班级名）
        List<TeachingTask> filtered = result.getRecords().stream().filter(t -> {
            if (courseName != null && !courseName.isBlank()) {
                String cn = courseNameMap.get(t.getCourseId());
                if (cn == null || !cn.contains(courseName)) return false;
            }
            if (teacherName != null && !teacherName.isBlank()) {
                String tn = teacherNameMap.get(t.getTeacherId());
                if (tn == null || !tn.contains(teacherName)) return false;
            }
            if (className != null && !className.isBlank()) {
                String cn = classNameMap.get(t.getClassId());
                if (cn == null || !cn.contains(className)) return false;
            }
            return true;
        }).peek(t -> {
            t.setCourseName(courseNameMap.get(t.getCourseId()));
            t.setTeacherName(teacherNameMap.get(t.getTeacherId()));
            t.setClassName(classNameMap.get(t.getClassId()));
            // 已排大节数：暂时为0，后续排课模块实现后从schedule表统计
            t.setScheduledSlots(0);
        }).collect(Collectors.toList());

        // 重建分页结果
        Page<TeachingTask> pageResult = new Page<>(page, size);
        pageResult.setRecords(filtered);
        pageResult.setTotal(filtered.size());
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<TeachingTask> getById(@PathVariable Long id) {
        TeachingTask task = teachingTaskMapper.selectById(id);
        if (task == null || task.getDeleted() == 1) {
            return Result.fail(404, "教学任务不存在");
        }
        fillRelation(task);
        task.setScheduledSlots(0);
        return Result.success(task);
    }

    @PostMapping
    public Result<TeachingTask> create(@Valid @RequestBody TaskForm form) {
        // 校验课程是否存在
        Course course = courseMapper.selectById(form.getCourseId());
        if (course == null || course.getDeleted() == 1) {
            return Result.fail(400, "所选课程不存在");
        }
        // 校验教师是否存在且启用
        Teacher teacher = teacherMapper.selectById(form.getTeacherId());
        if (teacher == null || teacher.getDeleted() == 1) {
            return Result.fail(400, "所选教师不存在");
        }
        if (teacher.getStatus() != 1) {
            return Result.fail(400, "所选教师已停用，无法创建教学任务");
        }
        // 校验班级是否存在且启用
        ClassInfo classInfo = classInfoMapper.selectById(form.getClassId());
        if (classInfo == null || classInfo.getDeleted() == 1) {
            return Result.fail(400, "所选班级不存在");
        }
        if (classInfo.getStatus() != 1) {
            return Result.fail(400, "所选班级已停用，无法创建教学任务");
        }

        TeachingTask task = new TeachingTask();
        task.setCourseId(form.getCourseId());
        task.setTeacherId(form.getTeacherId());
        task.setClassId(form.getClassId());
        task.setWeeklyHours(form.getWeeklyHours());
        task.setNeedContinuous(form.getNeedContinuous() != null ? form.getNeedContinuous() : 0);
        task.setStatus(form.getStatus() != null ? form.getStatus() : 1);
        task.setRemark(form.getRemark());
        task.setDeleted(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        teachingTaskMapper.insert(task);

        task.setCourseName(course.getCourseName());
        task.setTeacherName(teacher.getName());
        task.setClassName(classInfo.getClassName());
        task.setScheduledSlots(0);
        return Result.success(task);
    }

    @PutMapping("/{id}")
    public Result<TeachingTask> update(@PathVariable Long id, @Valid @RequestBody TaskForm form) {
        TeachingTask task = teachingTaskMapper.selectById(id);
        if (task == null || task.getDeleted() == 1) {
            return Result.fail(404, "教学任务不存在");
        }
        // 校验课程是否存在
        Course course = courseMapper.selectById(form.getCourseId());
        if (course == null || course.getDeleted() == 1) {
            return Result.fail(400, "所选课程不存在");
        }
        // 校验教师是否存在且启用
        Teacher teacher = teacherMapper.selectById(form.getTeacherId());
        if (teacher == null || teacher.getDeleted() == 1) {
            return Result.fail(400, "所选教师不存在");
        }
        if (teacher.getStatus() != 1) {
            return Result.fail(400, "所选教师已停用，无法创建教学任务");
        }
        // 校验班级是否存在且启用
        ClassInfo classInfo = classInfoMapper.selectById(form.getClassId());
        if (classInfo == null || classInfo.getDeleted() == 1) {
            return Result.fail(400, "所选班级不存在");
        }
        if (classInfo.getStatus() != 1) {
            return Result.fail(400, "所选班级已停用，无法创建教学任务");
        }

        task.setCourseId(form.getCourseId());
        task.setTeacherId(form.getTeacherId());
        task.setClassId(form.getClassId());
        task.setWeeklyHours(form.getWeeklyHours());
        task.setNeedContinuous(form.getNeedContinuous() != null ? form.getNeedContinuous() : 0);
        task.setStatus(form.getStatus());
        task.setRemark(form.getRemark());
        task.setUpdateTime(LocalDateTime.now());
        teachingTaskMapper.updateById(task);

        task.setCourseName(course.getCourseName());
        task.setTeacherName(teacher.getName());
        task.setClassName(classInfo.getClassName());
        task.setScheduledSlots(0);
        return Result.success(task);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        TeachingTask task = teachingTaskMapper.selectById(id);
        if (task == null || task.getDeleted() == 1) {
            return Result.fail(404, "教学任务不存在");
        }
        task.setDeleted(1);
        task.setUpdateTime(LocalDateTime.now());
        teachingTaskMapper.updateById(task);
        return Result.success("删除成功", null);
    }

    @GetMapping("/all")
    public Result<List<TeachingTask>> listAll() {
        List<TeachingTask> list = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getDeleted, 0)
                .eq(TeachingTask::getStatus, 1)
                .orderByDesc(TeachingTask::getCreateTime)
        );
        for (TeachingTask task : list) {
            fillRelation(task);
            task.setScheduledSlots(0);
        }
        return Result.success(list);
    }

    private void fillRelation(TeachingTask task) {
        Course course = courseMapper.selectById(task.getCourseId());
        if (course != null) task.setCourseName(course.getCourseName());
        Teacher teacher = teacherMapper.selectById(task.getTeacherId());
        if (teacher != null) task.setTeacherName(teacher.getName());
        ClassInfo classInfo = classInfoMapper.selectById(task.getClassId());
        if (classInfo != null) task.setClassName(classInfo.getClassName());
    }

    @Data
    public static class TaskForm {
        @NotNull(message = "课程不能为空")
        private Long courseId;
        @NotNull(message = "教师不能为空")
        private Long teacherId;
        @NotNull(message = "班级不能为空")
        private Long classId;
        @Min(value = 1, message = "每周课时必须大于0")
        private Integer weeklyHours;
        private Integer needContinuous;
        private Integer status;
        private String remark;
    }
}
