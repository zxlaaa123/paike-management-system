package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.service.TeacherService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@org.springframework.validation.annotation.Validated
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
        @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
        @RequestParam(defaultValue = "1") int page,
        @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
        @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
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
        @Size(max = 50, message = "教师编号不能超过50字符")
        private String teacherNo;
        @NotBlank(message = "教师姓名不能为空")
        @Size(max = 50, message = "教师姓名不能超过50字符")
        private String name;
        @Size(max = 100, message = "所属部门不能超过100字符")
        private String department;
        @Size(max = 30, message = "联系电话不能超过30字符")
        private String phone;
        private Integer status;
        @Size(max = 255, message = "备注不能超过255字符")
        private String remark;
    }

    @Data
    public static class StatusForm {
        @NotNull(message = "状态不能为空")
        private Integer status;
    }
}
