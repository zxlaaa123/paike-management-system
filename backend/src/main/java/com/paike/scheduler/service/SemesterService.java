package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.mapper.SemesterMapper;
import com.paike.scheduler.service.dto.SemesterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterMapper semesterMapper;

    public Page<Semester> list(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<Semester> wrapper = new LambdaQueryWrapper<Semester>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Semester::getName, keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Semester::getStatus, status);
        }
        wrapper.orderByDesc(Semester::getCreatedAt);
        return semesterMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Semester getById(Long id) {
        Semester semester = semesterMapper.selectById(id);
        if (semester == null) {
            throw new BusinessException("学期不存在");
        }
        return semester;
    }

    public Semester getCurrentSemester() {
        Semester semester = semesterMapper.selectOne(
                new LambdaQueryWrapper<Semester>().eq(Semester::getIsCurrent, 1));
        if (semester == null) {
            throw new BusinessException("当前未设置学期，请先创建或设置当前学期");
        }
        return semester;
    }

    public boolean hasCurrentSemester() {
        return semesterMapper.selectCount(
                new LambdaQueryWrapper<Semester>().eq(Semester::getIsCurrent, 1)) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public Semester create(SemesterRequest request) {
        long count = semesterMapper.selectCount(
                new LambdaQueryWrapper<Semester>().eq(Semester::getName, request.getName()));
        if (count > 0) {
            throw new BusinessException("学期名称已存在");
        }
        Semester semester = new Semester();
        semester.setName(request.getName());
        semester.setSchoolYear(request.getSchoolYear());
        semester.setTerm(request.getTerm());
        semester.setStartDate(request.getStartDate());
        semester.setEndDate(request.getEndDate());
        semester.setStatus(request.getStatus() != null ? request.getStatus() : "未开始");
        semester.setIsCurrent(0);
        semester.setRemark(request.getRemark());
        semester.setCreatedAt(LocalDateTime.now());
        semester.setUpdatedAt(LocalDateTime.now());
        semesterMapper.insert(semester);
        return semester;
    }

    @Transactional(rollbackFor = Exception.class)
    public Semester update(Long id, SemesterRequest request) {
        Semester semester = semesterMapper.selectById(id);
        if (semester == null) {
            throw new BusinessException("学期不存在");
        }
        long count = semesterMapper.selectCount(
                new LambdaQueryWrapper<Semester>()
                        .eq(Semester::getName, request.getName())
                        .ne(Semester::getId, id));
        if (count > 0) {
            throw new BusinessException("学期名称已存在");
        }
        semester.setName(request.getName());
        semester.setSchoolYear(request.getSchoolYear());
        semester.setTerm(request.getTerm());
        semester.setStartDate(request.getStartDate());
        semester.setEndDate(request.getEndDate());
        semester.setStatus(request.getStatus());
        semester.setRemark(request.getRemark());
        semester.setUpdatedAt(LocalDateTime.now());
        semesterMapper.updateById(semester);
        return semester;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Semester semester = semesterMapper.selectById(id);
        if (semester == null) {
            throw new BusinessException("学期不存在");
        }
        if (semester.getIsCurrent() != null && semester.getIsCurrent() == 1) {
            throw new BusinessException("当前学期不能直接删除，请先设置其他学期为当前学期");
        }
        semesterMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setCurrent(Long id) {
        Semester semester = semesterMapper.selectById(id);
        if (semester == null) {
            throw new BusinessException("学期不存在");
        }
        // 先将所有学期的 is_current 设为 0
        Semester clearCurrent = new Semester();
        clearCurrent.setIsCurrent(0);
        clearCurrent.setUpdatedAt(LocalDateTime.now());
        semesterMapper.update(clearCurrent, new LambdaQueryWrapper<Semester>().eq(Semester::getIsCurrent, 1));

        // 再将目标学期设为当前
        semester.setIsCurrent(1);
        semester.setUpdatedAt(LocalDateTime.now());
        semesterMapper.updateById(semester);
    }

    public List<Semester> listAll() {
        return semesterMapper.selectList(
                new LambdaQueryWrapper<Semester>().orderByDesc(Semester::getCreatedAt));
    }
}
