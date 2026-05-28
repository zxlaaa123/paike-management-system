package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.service.ClassInfoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class ClassInfoController {

    private final ClassInfoService classInfoService;

    @GetMapping
    public Result<Page<ClassInfo>> list(
        @RequestParam(required = false) String className,
        @RequestParam(required = false) String major,
        @RequestParam(required = false) String grade,
        @RequestParam(required = false) Integer status,
        @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
        @RequestParam(defaultValue = "1") int page,
        @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
        @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
        @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(classInfoService.list(className, major, grade, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<ClassInfo> getById(@PathVariable Long id) {
        return Result.success(classInfoService.getById(id));
    }

    @PostMapping
    public Result<ClassInfo> create(@Valid @RequestBody ClassForm form) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName(form.getClassName());
        classInfo.setMajor(form.getMajor());
        classInfo.setGrade(form.getGrade());
        classInfo.setStudentCount(form.getStudentCount());
        classInfo.setHeadTeacher(form.getHeadTeacher());
        classInfo.setStatus(form.getStatus() != null ? form.getStatus() : 1);
        classInfo.setRemark(form.getRemark());
        return Result.success(classInfoService.create(classInfo));
    }

    @PutMapping("/{id}")
    public Result<ClassInfo> update(@PathVariable Long id, @Valid @RequestBody ClassForm form) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName(form.getClassName());
        classInfo.setMajor(form.getMajor());
        classInfo.setGrade(form.getGrade());
        classInfo.setStudentCount(form.getStudentCount());
        classInfo.setHeadTeacher(form.getHeadTeacher());
        classInfo.setStatus(form.getStatus());
        classInfo.setRemark(form.getRemark());
        return Result.success(classInfoService.update(id, classInfo));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        classInfoService.delete(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        classInfoService.updateStatus(id, form.getStatus());
        return Result.success("操作成功", null);
    }

    @GetMapping("/all")
    public Result<List<ClassInfo>> listAll() {
        return Result.success(classInfoService.listAll());
    }

    @Getter
    public static class ClassForm {
        @NotBlank(message = "班级名称不能为空")
        @Size(max = 100, message = "班级名称不能超过100字符")
        private String className;
        @Size(max = 100, message = "专业名称不能超过100字符")
        private String major;
        @Size(max = 20, message = "年级不能超过20字符")
        private String grade;
        @Min(value = 1, message = "班级人数必须大于0")
        private Integer studentCount;
        @Size(max = 50, message = "班主任不能超过50字符")
        private String headTeacher;
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
