package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.mapper.CourseMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseMapper courseMapper;

    @GetMapping
    public Result<Page<Course>> list(
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) String courseNo,
        @RequestParam(required = false) String courseType,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
            .eq(Course::getDeleted, 0);
        if (courseName != null && !courseName.isBlank()) {
            wrapper.like(Course::getCourseName, courseName);
        }
        if (courseNo != null && !courseNo.isBlank()) {
            wrapper.like(Course::getCourseNo, courseNo);
        }
        if (courseType != null && !courseType.isBlank()) {
            wrapper.eq(Course::getCourseType, courseType);
        }
        wrapper.orderByDesc(Course::getCreateTime);
        Page<Course> result = courseMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Course> getById(@PathVariable Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null || course.getDeleted() == 1) {
            return Result.fail(404, "课程不存在");
        }
        return Result.success(course);
    }

    @PostMapping
    public Result<Course> create(@Valid @RequestBody CourseForm form) {
        long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
            .eq(Course::getCourseNo, form.getCourseNo())
            .eq(Course::getDeleted, 0));
        if (count > 0) {
            return Result.fail(400, "课程编号已存在");
        }
        Course course = new Course();
        course.setCourseNo(form.getCourseNo());
        course.setCourseName(form.getCourseName());
        course.setCourseType(form.getCourseType());
        course.setCourseNature(form.getCourseNature());
        course.setTotalHours(form.getTotalHours());
        course.setWeeklyHours(form.getWeeklyHours());
        course.setRemark(form.getRemark());
        course.setDeleted(0);
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());
        courseMapper.insert(course);
        return Result.success(course);
    }

    @PutMapping("/{id}")
    public Result<Course> update(@PathVariable Long id, @Valid @RequestBody CourseForm form) {
        Course course = courseMapper.selectById(id);
        if (course == null || course.getDeleted() == 1) {
            return Result.fail(404, "课程不存在");
        }
        long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
            .eq(Course::getCourseNo, form.getCourseNo())
            .eq(Course::getDeleted, 0)
            .ne(Course::getId, id));
        if (count > 0) {
            return Result.fail(400, "课程编号已存在");
        }
        course.setCourseNo(form.getCourseNo());
        course.setCourseName(form.getCourseName());
        course.setCourseType(form.getCourseType());
        course.setCourseNature(form.getCourseNature());
        course.setTotalHours(form.getTotalHours());
        course.setWeeklyHours(form.getWeeklyHours());
        course.setRemark(form.getRemark());
        course.setUpdateTime(LocalDateTime.now());
        courseMapper.updateById(course);
        return Result.success(course);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null || course.getDeleted() == 1) {
            return Result.fail(404, "课程不存在");
        }
        course.setDeleted(1);
        course.setUpdateTime(LocalDateTime.now());
        courseMapper.updateById(course);
        return Result.success("删除成功", null);
    }

    @GetMapping("/all")
    public Result<List<Course>> listAll() {
        List<Course> list = courseMapper.selectList(new LambdaQueryWrapper<Course>()
            .eq(Course::getDeleted, 0)
            .orderByAsc(Course::getCourseNo));
        return Result.success(list);
    }

    @Data
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
