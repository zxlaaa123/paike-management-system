package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;

    public Page<Course> list(String courseName, String courseNo, String courseType, int page, int size) {
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
        return courseMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Course getById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null || course.getDeleted() == 1) {
            throw new BusinessException(404, "课程不存在");
        }
        return course;
    }

    @Transactional(rollbackFor = Exception.class)
    public Course create(Course course) {
        long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
            .eq(Course::getCourseNo, course.getCourseNo())
            .eq(Course::getDeleted, 0));
        if (count > 0) {
            throw new BusinessException(400, "课程编号已存在");
        }
        course.setDeleted(0);
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());
        courseMapper.insert(course);
        return course;
    }

    @Transactional(rollbackFor = Exception.class)
    public Course update(Long id, Course course) {
        Course existing = getById(id);
        long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
            .eq(Course::getCourseNo, course.getCourseNo())
            .eq(Course::getDeleted, 0)
            .ne(Course::getId, id));
        if (count > 0) {
            throw new BusinessException(400, "课程编号已存在");
        }
        existing.setCourseNo(course.getCourseNo());
        existing.setCourseName(course.getCourseName());
        existing.setCourseType(course.getCourseType());
        existing.setCourseNature(course.getCourseNature());
        existing.setTotalHours(course.getTotalHours());
        existing.setWeeklyHours(course.getWeeklyHours());
        existing.setRemark(course.getRemark());
        existing.setUpdateTime(LocalDateTime.now());
        courseMapper.updateById(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        courseMapper.deleteById(id);
    }

    public List<Course> listAll() {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
            .eq(Course::getDeleted, 0)
            .orderByAsc(Course::getCourseNo));
    }
}
