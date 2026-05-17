package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.service.TeacherService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    @GetMapping
    public Result<Page<Teacher>> list(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String teacherNo,
        @RequestParam(required = false) String department,
        @RequestParam(required = false) Integer status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(teacherService.list(name, teacherNo, department, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Teacher> getById(@PathVariable Long id) {
        return Result.success(teacherService.getById(id));
    }

    @PostMapping
    public Result<Teacher> create(@Valid @RequestBody TeacherForm form) {
        Teacher teacher = new Teacher();
        teacher.setTeacherNo(form.getTeacherNo());
        teacher.setName(form.getName());
        teacher.setDepartment(form.getDepartment());
        teacher.setPhone(form.getPhone());
        teacher.setStatus(form.getStatus() != null ? form.getStatus() : 1);
        teacher.setRemark(form.getRemark());
        return Result.success(teacherService.create(teacher));
    }

    @PutMapping("/{id}")
    public Result<Teacher> update(@PathVariable Long id, @Valid @RequestBody TeacherForm form) {
        Teacher teacher = new Teacher();
        teacher.setTeacherNo(form.getTeacherNo());
        teacher.setName(form.getName());
        teacher.setDepartment(form.getDepartment());
        teacher.setPhone(form.getPhone());
        teacher.setStatus(form.getStatus());
        teacher.setRemark(form.getRemark());
        return Result.success(teacherService.update(id, teacher));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        teacherService.delete(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        teacherService.updateStatus(id, form.getStatus());
        return Result.success("操作成功", null);
    }

    @GetMapping("/all")
    public Result<List<Teacher>> listAll() {
        return Result.success(teacherService.listAll());
    }

    @Getter
    public static class TeacherForm {
        @NotBlank(message = "教师编号不能为空")
        private String teacherNo;
        @NotBlank(message = "教师姓名不能为空")
        private String name;
        private String department;
        private String phone;
        private Integer status;
        private String remark;
    }

    @Data
    public static class StatusForm {
        @NotNull(message = "状态不能为空")
        private Integer status;
    }
}
