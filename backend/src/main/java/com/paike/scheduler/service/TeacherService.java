package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.mapper.TeacherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherMapper teacherMapper;

    public Page<Teacher> list(String name, String teacherNo, String department, Integer status, int page, int size) {
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
        return teacherMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Teacher getById(Long id) {
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null || teacher.getDeleted() == 1) {
            throw new BusinessException(404, "教师不存在");
        }
        return teacher;
    }

    @Transactional(rollbackFor = Exception.class)
    public Teacher create(Teacher teacher) {
        long count = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>()
            .eq(Teacher::getTeacherNo, teacher.getTeacherNo())
            .eq(Teacher::getDeleted, 0));
        if (count > 0) {
            throw new BusinessException(400, "教师编号已存在");
        }
        teacher.setDeleted(0);
        teacher.setCreateTime(LocalDateTime.now());
        teacher.setUpdateTime(LocalDateTime.now());
        teacherMapper.insert(teacher);
        return teacher;
    }

    @Transactional(rollbackFor = Exception.class)
    public Teacher update(Long id, Teacher teacher) {
        Teacher existing = getById(id);
        long count = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>()
            .eq(Teacher::getTeacherNo, teacher.getTeacherNo())
            .eq(Teacher::getDeleted, 0)
            .ne(Teacher::getId, id));
        if (count > 0) {
            throw new BusinessException(400, "教师编号已存在");
        }
        existing.setTeacherNo(teacher.getTeacherNo());
        existing.setName(teacher.getName());
        existing.setDepartment(teacher.getDepartment());
        existing.setPhone(teacher.getPhone());
        existing.setStatus(teacher.getStatus());
        existing.setRemark(teacher.getRemark());
        existing.setUpdateTime(LocalDateTime.now());
        teacherMapper.updateById(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        teacherMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Teacher teacher = getById(id);
        teacher.setStatus(status);
        teacher.setUpdateTime(LocalDateTime.now());
        teacherMapper.updateById(teacher);
    }

    public List<Teacher> listAll() {
        return teacherMapper.selectList(new LambdaQueryWrapper<Teacher>()
            .eq(Teacher::getDeleted, 0)
            .eq(Teacher::getStatus, 1)
            .orderByAsc(Teacher::getTeacherNo));
    }
}
