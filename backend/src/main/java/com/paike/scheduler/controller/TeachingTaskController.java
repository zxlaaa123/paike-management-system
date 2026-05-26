package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.SemesterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teaching-tasks")
@RequiredArgsConstructor
@Slf4j
public class TeachingTaskController {

    private final TeachingTaskMapper teachingTaskMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ScheduleMapper scheduleMapper;
    private final SemesterService semesterService;

    @GetMapping
    public Result<Page<TeachingTask>> list(
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) String teacherName,
        @RequestParam(required = false) String className,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) Long semesterId,
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "10") int pageSize
    ) {
        // 如果传了 semesterId 则按指定学期查，否则默认按当前学期查
        Long resolvedSemesterId = semesterId;
        if (resolvedSemesterId == null) {
            try {
                Semester current = semesterService.getCurrentSemester();
                resolvedSemesterId = current.getId();
            } catch (BusinessException e) {
                // 没有当前学期时返回空结果
                log.warn("未找到当前学期，教学任务列表按业务约定返回空分页，前端显示空列表", e);
                return Result.success(new Page<>(pageNum, pageSize));
            }
        }
        // 数据库层面分页 + 过滤
        Page<TeachingTask> pageResult = new Page<>(pageNum, pageSize);
        List<TeachingTask> records = teachingTaskMapper.selectFilteredTaskIds(
            courseName, teacherName, className, status, resolvedSemesterId, pageResult);
        pageResult.setRecords(records);

        if (records.isEmpty()) {
            return Result.success(pageResult);
        }

        // 批量填充关联数据
        fillTaskRelations(records);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<TeachingTask> getById(@PathVariable Long id) {
        TeachingTask task = teachingTaskMapper.selectById(id);
        if (task == null || task.getDeleted() == 1) {
            throw new BusinessException(404, "教学任务不存在");
        }
        fillTaskRelations(List.of(task));
        return Result.success(task);
    }

    @PostMapping
    public Result<TeachingTask> create(@Valid @RequestBody TaskForm form) {
        // 校验课程是否存在
        Course course = courseMapper.selectById(form.getCourseId());
        if (course == null || course.getDeleted() == 1) {
            throw new BusinessException(400, "所选课程不存在");
        }
        // 校验教师是否存在且启用
        Teacher teacher = teacherMapper.selectById(form.getTeacherId());
        if (teacher == null || teacher.getDeleted() == 1) {
            throw new BusinessException(400, "所选教师不存在");
        }
        if (teacher.getStatus() != 1) {
            throw new BusinessException(400, "所选教师已停用，无法创建教学任务");
        }
        // 校验班级是否存在且启用
        ClassInfo classInfo = classInfoMapper.selectById(form.getClassId());
        if (classInfo == null || classInfo.getDeleted() == 1) {
            throw new BusinessException(400, "所选班级不存在");
        }
        if (classInfo.getStatus() != 1) {
            throw new BusinessException(400, "所选班级已停用，无法创建教学任务");
        }

        TeachingTask task = new TeachingTask();
        task.setSemesterId(semesterService.getCurrentSemester().getId());
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
            throw new BusinessException(404, "教学任务不存在");
        }
        // 校验课程是否存在
        Course course = courseMapper.selectById(form.getCourseId());
        if (course == null || course.getDeleted() == 1) {
            throw new BusinessException(400, "所选课程不存在");
        }
        // 校验教师是否存在且启用
        Teacher teacher = teacherMapper.selectById(form.getTeacherId());
        if (teacher == null || teacher.getDeleted() == 1) {
            throw new BusinessException(400, "所选教师不存在");
        }
        if (teacher.getStatus() != 1) {
            throw new BusinessException(400, "所选教师已停用，无法创建教学任务");
        }
        // 校验班级是否存在且启用
        ClassInfo classInfo = classInfoMapper.selectById(form.getClassId());
        if (classInfo == null || classInfo.getDeleted() == 1) {
            throw new BusinessException(400, "所选班级不存在");
        }
        if (classInfo.getStatus() != 1) {
            throw new BusinessException(400, "所选班级已停用，无法创建教学任务");
        }

        task.setCourseId(form.getCourseId());
        task.setTeacherId(form.getTeacherId());
        task.setClassId(form.getClassId());
        task.setWeeklyHours(form.getWeeklyHours());
        task.setNeedContinuous(form.getNeedContinuous() != null ? form.getNeedContinuous() : 0);
        if (form.getStatus() != null) {
            task.setStatus(form.getStatus());
        }
        task.setRemark(form.getRemark());
        task.setUpdateTime(LocalDateTime.now());
        teachingTaskMapper.updateById(task);

        task.setCourseName(course.getCourseName());
        task.setTeacherName(teacher.getName());
        task.setClassName(classInfo.getClassName());
        Long count = scheduleMapper.selectCount(
            new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getTeachingTaskId, task.getId())
                .eq(Schedule::getDeleted, 0));
        task.setScheduledSlots(count != null ? count.intValue() : 0);
        return Result.success(task);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        TeachingTask task = teachingTaskMapper.selectById(id);
        if (task == null || task.getDeleted() == 1) {
            throw new BusinessException(404, "教学任务不存在");
        }
        teachingTaskMapper.deleteById(id);
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
        fillTaskRelations(list);
        return Result.success(list);
    }

    /** 批量填充教学任务关联数据（避免 N+1 查询） */
    private void fillTaskRelations(List<TeachingTask> tasks) {
        if (tasks.isEmpty()) return;

        List<Long> courseIds = tasks.stream().map(TeachingTask::getCourseId).distinct().collect(Collectors.toList());
        List<Long> teacherIds = tasks.stream().map(TeachingTask::getTeacherId).distinct().collect(Collectors.toList());
        List<Long> classIds = tasks.stream().map(TeachingTask::getClassId).distinct().collect(Collectors.toList());
        List<Long> taskIds = tasks.stream().map(TeachingTask::getId).distinct().collect(Collectors.toList());

        Map<Long, String> courseNameMap = courseIds.isEmpty() ? Map.of() :
            courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName));
        Map<Long, String> teacherNameMap = teacherIds.isEmpty() ? Map.of() :
            teacherMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Teacher::getName));
        Map<Long, String> classNameMap = classIds.isEmpty() ? Map.of() :
            classInfoMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassInfo::getId, ClassInfo::getClassName));
        Map<Long, Long> scheduledCountMap = taskIds.isEmpty() ? Map.of() :
            scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                    .in(Schedule::getTeachingTaskId, taskIds)
                    .eq(Schedule::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(Schedule::getTeachingTaskId, Collectors.counting()));

        for (TeachingTask t : tasks) {
            t.setCourseName(courseNameMap.get(t.getCourseId()));
            t.setTeacherName(teacherNameMap.get(t.getTeacherId()));
            t.setClassName(classNameMap.get(t.getClassId()));
            t.setScheduledSlots(scheduledCountMap.getOrDefault(t.getId(), 0L).intValue());
        }
    }

    @Getter
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
