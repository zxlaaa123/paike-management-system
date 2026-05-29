package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.mapper.ClassInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassInfoService {

    private final ClassInfoMapper classInfoMapper;

    public Page<ClassInfo> list(String className, String major, String grade, Integer status, int page, int size) {
        LambdaQueryWrapper<ClassInfo> wrapper = new LambdaQueryWrapper<ClassInfo>();
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
        return classInfoMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public ClassInfo getById(Long id) {
        ClassInfo classInfo = classInfoMapper.selectById(id);
        if (classInfo == null || Integer.valueOf(1).equals(classInfo.getDeleted())) {
            throw new BusinessException(404, "班级不存在");
        }
        return classInfo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassInfo create(ClassInfo classInfo) {
        long count = classInfoMapper.selectCount(new LambdaQueryWrapper<ClassInfo>()
            .eq(ClassInfo::getClassName, classInfo.getClassName()));
        if (count > 0) {
            throw new BusinessException(400, "班级名称已存在");
        }
        classInfo.setDeleted(0);
        classInfo.setCreateTime(LocalDateTime.now());
        classInfo.setUpdateTime(LocalDateTime.now());
        classInfoMapper.insert(classInfo);
        return classInfo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassInfo update(Long id, ClassInfo classInfo) {
        ClassInfo existing = getById(id);
        long count = classInfoMapper.selectCount(new LambdaQueryWrapper<ClassInfo>()
            .eq(ClassInfo::getClassName, classInfo.getClassName())
            .ne(ClassInfo::getId, id));
        if (count > 0) {
            throw new BusinessException(400, "班级名称已存在");
        }
        existing.setClassName(classInfo.getClassName());
        existing.setMajor(classInfo.getMajor());
        existing.setGrade(classInfo.getGrade());
        existing.setStudentCount(classInfo.getStudentCount());
        existing.setHeadTeacher(classInfo.getHeadTeacher());
        existing.setStatus(classInfo.getStatus());
        existing.setRemark(classInfo.getRemark());
        existing.setUpdateTime(LocalDateTime.now());
        classInfoMapper.updateById(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        classInfoMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        ClassInfo classInfo = getById(id);
        classInfo.setStatus(status);
        classInfo.setUpdateTime(LocalDateTime.now());
        classInfoMapper.updateById(classInfo);
    }

    public List<ClassInfo> listAll() {
        return classInfoMapper.selectList(new LambdaQueryWrapper<ClassInfo>()
            .eq(ClassInfo::getStatus, 1)
            .orderByAsc(ClassInfo::getClassName));
    }
}
