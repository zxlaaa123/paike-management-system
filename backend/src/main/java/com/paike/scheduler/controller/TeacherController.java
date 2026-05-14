package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.mapper.TeacherMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherMapper teacherMapper;

    @GetMapping
    public Result<Page<Teacher>> list(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String teacherNo,
        @RequestParam(required = false) String department,
        @RequestParam(required = false) Integer status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<Teacher>()
            .eq(Teacher::getDeleted, 0);
        if (name != null && !name.isBlank()) {
            wrapper.like(Teacher::getName, name);
        }
        if (teacherNo != null && !teacherNo.isBlank()) {
            wrapper.like(Teacher::getTeacherNo, teacherNo);
        }
        if (department != null && !department.isBlank()) {
            wrapper.like(Teacher::getDepartment, department);
        }
        if (status != null) {
            wrapper.eq(Teacher::getStatus, status);
        }
        wrapper.orderByDesc(Teacher::getCreateTime);
        Page<Teacher> result = teacherMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Teacher> getById(@PathVariable Long id) {
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null || teacher.getDeleted() == 1) {
            return Result.fail(404, "教师不存在");
        }
        return Result.success(teacher);
    }

    @PostMapping
    public Result<Teacher> create(@Valid @RequestBody TeacherForm form) {
        long count = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>()
            .eq(Teacher::getTeacherNo, form.getTeacherNo())
            .eq(Teacher::getDeleted, 0));
        if (count > 0) {
            return Result.fail(400, "教师编号已存在");
        }
        Teacher teacher = new Teacher();
        teacher.setTeacherNo(form.getTeacherNo());
        teacher.setName(form.getName());
        teacher.setDepartment(form.getDepartment());
        teacher.setPhone(form.getPhone());
        teacher.setStatus(form.getStatus() != null ? form.getStatus() : 1);
        teacher.setRemark(form.getRemark());
        teacher.setDeleted(0);
        teacher.setCreateTime(LocalDateTime.now());
        teacher.setUpdateTime(LocalDateTime.now());
        teacherMapper.insert(teacher);
        return Result.success(teacher);
    }

    @PutMapping("/{id}")
    public Result<Teacher> update(@PathVariable Long id, @Valid @RequestBody TeacherForm form) {
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null || teacher.getDeleted() == 1) {
            return Result.fail(404, "教师不存在");
        }
        long count = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>()
            .eq(Teacher::getTeacherNo, form.getTeacherNo())
            .eq(Teacher::getDeleted, 0)
            .ne(Teacher::getId, id));
        if (count > 0) {
            return Result.fail(400, "教师编号已存在");
        }
        teacher.setTeacherNo(form.getTeacherNo());
        teacher.setName(form.getName());
        teacher.setDepartment(form.getDepartment());
        teacher.setPhone(form.getPhone());
        teacher.setStatus(form.getStatus());
        teacher.setRemark(form.getRemark());
        teacher.setUpdateTime(LocalDateTime.now());
        teacherMapper.updateById(teacher);
        return Result.success(teacher);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null || teacher.getDeleted() == 1) {
            return Result.fail(404, "教师不存在");
        }
        teacherMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null || teacher.getDeleted() == 1) {
            return Result.fail(404, "教师不存在");
        }
        teacher.setStatus(form.getStatus());
        teacher.setUpdateTime(LocalDateTime.now());
        teacherMapper.updateById(teacher);
        return Result.success("操作成功", null);
    }

    @GetMapping("/all")
    public Result<List<Teacher>> listAll() {
        List<Teacher> list = teacherMapper.selectList(new LambdaQueryWrapper<Teacher>()
            .eq(Teacher::getDeleted, 0)
            .eq(Teacher::getStatus, 1)
            .orderByAsc(Teacher::getTeacherNo));
        return Result.success(list);
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
