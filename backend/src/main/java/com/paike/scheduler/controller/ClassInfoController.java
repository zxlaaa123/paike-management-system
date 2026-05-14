package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.mapper.ClassInfoMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class ClassInfoController {

    private final ClassInfoMapper classInfoMapper;

    @GetMapping
    public Result<Page<ClassInfo>> list(
        @RequestParam(required = false) String className,
        @RequestParam(required = false) String major,
        @RequestParam(required = false) String grade,
        @RequestParam(required = false) Integer status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        LambdaQueryWrapper<ClassInfo> wrapper = new LambdaQueryWrapper<ClassInfo>()
            .eq(ClassInfo::getDeleted, 0);
        if (className != null && !className.isBlank()) {
            wrapper.like(ClassInfo::getClassName, className);
        }
        if (major != null && !major.isBlank()) {
            wrapper.like(ClassInfo::getMajor, major);
        }
        if (grade != null && !grade.isBlank()) {
            wrapper.eq(ClassInfo::getGrade, grade);
        }
        if (status != null) {
            wrapper.eq(ClassInfo::getStatus, status);
        }
        wrapper.orderByDesc(ClassInfo::getCreateTime);
        Page<ClassInfo> result = classInfoMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<ClassInfo> getById(@PathVariable Long id) {
        ClassInfo classInfo = classInfoMapper.selectById(id);
        if (classInfo == null || classInfo.getDeleted() == 1) {
            return Result.fail(404, "班级不存在");
        }
        return Result.success(classInfo);
    }

    @PostMapping
    public Result<ClassInfo> create(@Valid @RequestBody ClassForm form) {
        long count = classInfoMapper.selectCount(new LambdaQueryWrapper<ClassInfo>()
            .eq(ClassInfo::getClassName, form.getClassName())
            .eq(ClassInfo::getDeleted, 0));
        if (count > 0) {
            return Result.fail(400, "班级名称已存在");
        }
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName(form.getClassName());
        classInfo.setMajor(form.getMajor());
        classInfo.setGrade(form.getGrade());
        classInfo.setStudentCount(form.getStudentCount());
        classInfo.setHeadTeacher(form.getHeadTeacher());
        classInfo.setStatus(form.getStatus() != null ? form.getStatus() : 1);
        classInfo.setRemark(form.getRemark());
        classInfo.setDeleted(0);
        classInfo.setCreateTime(LocalDateTime.now());
        classInfo.setUpdateTime(LocalDateTime.now());
        classInfoMapper.insert(classInfo);
        return Result.success(classInfo);
    }

    @PutMapping("/{id}")
    public Result<ClassInfo> update(@PathVariable Long id, @Valid @RequestBody ClassForm form) {
        ClassInfo classInfo = classInfoMapper.selectById(id);
        if (classInfo == null || classInfo.getDeleted() == 1) {
            return Result.fail(404, "班级不存在");
        }
        long count = classInfoMapper.selectCount(new LambdaQueryWrapper<ClassInfo>()
            .eq(ClassInfo::getClassName, form.getClassName())
            .eq(ClassInfo::getDeleted, 0)
            .ne(ClassInfo::getId, id));
        if (count > 0) {
            return Result.fail(400, "班级名称已存在");
        }
        classInfo.setClassName(form.getClassName());
        classInfo.setMajor(form.getMajor());
        classInfo.setGrade(form.getGrade());
        classInfo.setStudentCount(form.getStudentCount());
        classInfo.setHeadTeacher(form.getHeadTeacher());
        classInfo.setStatus(form.getStatus());
        classInfo.setRemark(form.getRemark());
        classInfo.setUpdateTime(LocalDateTime.now());
        classInfoMapper.updateById(classInfo);
        return Result.success(classInfo);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ClassInfo classInfo = classInfoMapper.selectById(id);
        if (classInfo == null || classInfo.getDeleted() == 1) {
            return Result.fail(404, "班级不存在");
        }
        classInfoMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        ClassInfo classInfo = classInfoMapper.selectById(id);
        if (classInfo == null || classInfo.getDeleted() == 1) {
            return Result.fail(404, "班级不存在");
        }
        classInfo.setStatus(form.getStatus());
        classInfo.setUpdateTime(LocalDateTime.now());
        classInfoMapper.updateById(classInfo);
        return Result.success("操作成功", null);
    }

    @GetMapping("/all")
    public Result<List<ClassInfo>> listAll() {
        List<ClassInfo> list = classInfoMapper.selectList(new LambdaQueryWrapper<ClassInfo>()
            .eq(ClassInfo::getDeleted, 0)
            .eq(ClassInfo::getStatus, 1)
            .orderByAsc(ClassInfo::getClassName));
        return Result.success(list);
    }

    @Getter
    public static class ClassForm {
        @NotBlank(message = "班级名称不能为空")
        private String className;
        private String major;
        private String grade;
        @Min(value = 1, message = "班级人数必须大于0")
        private Integer studentCount;
        private String headTeacher;
        private Integer status;
        private String remark;
    }

    @Data
    public static class StatusForm {
        @NotNull(message = "状态不能为空")
        private Integer status;
    }
}
