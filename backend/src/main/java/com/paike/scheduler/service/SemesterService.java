package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.mapper.SemesterMapper;
import com.paike.scheduler.service.dto.SemesterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
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
        // 防御性读取：DB 层 v6 已加唯一约束，但若历史脏数据残留多行也不能直接 selectOne 抛 TooManyResultsException。
        List<Semester> rows = semesterMapper.selectList(
                new LambdaQueryWrapper<Semester>()
                        .eq(Semester::getIsCurrent, 1)
                        .orderByDesc(Semester::getUpdatedAt)
                        .orderByDesc(Semester::getId));
        if (rows.isEmpty()) {
            throw new BusinessException("当前未设置学期，请先创建或设置当前学期");
        }
        if (rows.size() > 1) {
            log.warn("检测到 {} 个学期 is_current=1，取 updated_at 最新的一行（id={}）。请检查数据一致性。",
                    rows.size(), rows.get(0).getId());
        }
        return rows.get(0);
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
        try {
            // 先将所有学期的 is_current 设为 0
            Semester clearCurrent = new Semester();
            clearCurrent.setIsCurrent(0);
            clearCurrent.setUpdatedAt(LocalDateTime.now());
            semesterMapper.update(clearCurrent, new LambdaQueryWrapper<Semester>().eq(Semester::getIsCurrent, 1));

            // 再将目标学期设为当前
            semester.setIsCurrent(1);
            semester.setUpdatedAt(LocalDateTime.now());
            semesterMapper.updateById(semester);
        } catch (DuplicateKeyException e) {
            // v6 在 semester 上加了 UNIQUE(current_marker)，若两个请求并发执行 setCurrent，
            // DB 会拒绝第二个事务的 is_current=1。这里向调用方抛 409 让前端重试即可。
            log.warn("setCurrent 冲突：另一并发请求已抢先把其他学期设为当前", e);
            throw new BusinessException(409, "并发设置当前学期失败，请稍后重试");
        }
    }

    public List<Semester> listAll() {
        return semesterMapper.selectList(
                new LambdaQueryWrapper<Semester>().orderByDesc(Semester::getCreatedAt));
    }
}
