package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.service.TeachingTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/teaching-tasks")
@RequiredArgsConstructor
public class TeachingTaskController {

    private final TeachingTaskService teachingTaskService;

    @GetMapping
    public Result<Page<TeachingTask>> list(
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) String teacherName,
        @RequestParam(required = false) String className,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) Long semesterId,
        @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
        @RequestParam(defaultValue = "1") int pageNum,
        @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
        @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
        @RequestParam(defaultValue = "10") int pageSize
    ) {
        return Result.success(teachingTaskService.list(courseName, teacherName, className, status, semesterId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<TeachingTask> getById(@PathVariable Long id) {
        return Result.success(teachingTaskService.getById(id));
    }

    @PostMapping
    public Result<TeachingTask> create(@Valid @RequestBody TaskForm form) {
        return Result.success(teachingTaskService.create(
            form.getCourseId(),
            form.getTeacherId(),
            form.getClassId(),
            form.getWeeklyHours(),
            form.getNeedContinuous(),
            form.getStatus(),
            form.getRemark()));
    }

    @PutMapping("/{id}")
    public Result<TeachingTask> update(@PathVariable Long id, @Valid @RequestBody TaskForm form) {
        return Result.success(teachingTaskService.update(
            id,
            form.getCourseId(),
            form.getTeacherId(),
            form.getClassId(),
            form.getWeeklyHours(),
            form.getNeedContinuous(),
            form.getStatus(),
            form.getRemark()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        teachingTaskService.delete(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/all")
    public Result<List<TeachingTask>> listAll() {
        return Result.success(teachingTaskService.listAll());
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
        @Size(max = 255, message = "备注不能超过255字符")
        private String remark;
    }
}
