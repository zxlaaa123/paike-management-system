package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.service.CourseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public Result<Page<Course>> list(
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) String courseNo,
        @RequestParam(required = false) String courseType,
        @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
        @RequestParam(defaultValue = "1") int page,
        @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
        @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
        @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(courseService.list(courseName, courseNo, courseType, page, size));
    }

    @GetMapping("/{id}")
    public Result<Course> getById(@PathVariable Long id) {
        return Result.success(courseService.getById(id));
    }

    @PostMapping
    public Result<Course> create(@Valid @RequestBody CourseForm form) {
        Course course = new Course();
        course.setCourseNo(form.getCourseNo());
        course.setCourseName(form.getCourseName());
        course.setCourseType(form.getCourseType());
        course.setCourseNature(form.getCourseNature());
        course.setTotalHours(form.getTotalHours());
        course.setWeeklyHours(form.getWeeklyHours());
        course.setRemark(form.getRemark());
        return Result.success(courseService.create(course));
    }

    @PutMapping("/{id}")
    public Result<Course> update(@PathVariable Long id, @Valid @RequestBody CourseForm form) {
        Course course = new Course();
        course.setCourseNo(form.getCourseNo());
        course.setCourseName(form.getCourseName());
        course.setCourseType(form.getCourseType());
        course.setCourseNature(form.getCourseNature());
        course.setTotalHours(form.getTotalHours());
        course.setWeeklyHours(form.getWeeklyHours());
        course.setRemark(form.getRemark());
        return Result.success(courseService.update(id, course));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/all")
    public Result<List<Course>> listAll() {
        return Result.success(courseService.listAll());
    }

    @Getter
    public static class CourseForm {
        @NotBlank(message = "课程编号不能为空")
        private String courseNo;
        @NotBlank(message = "课程名称不能为空")
        private String courseName;
        private String courseType;
        private String courseNature;
        @Min(value = 1, message = "总学时必须大于0")
        private Integer totalHours;
        @Min(value = 1, message = "每周课时必须大于0")
        private Integer weeklyHours;
        private String remark;
    }
}
