package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchedulePlanService {

    private final SchedulePlanMapper planMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;

    public Page<SchedulePlan> list(Long semesterId, String status, String strategyType, String keyword, int page, int size) {
        LambdaQueryWrapper<SchedulePlan> wrapper = new LambdaQueryWrapper<SchedulePlan>()
                .eq(SchedulePlan::getSemesterId, semesterId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SchedulePlan::getStatus, status);
        }
        if (strategyType != null && !strategyType.isBlank()) {
            wrapper.eq(SchedulePlan::getStrategyType, strategyType);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SchedulePlan::getName, keyword);
        }
        wrapper.orderByDesc(SchedulePlan::getCreatedAt);
        return planMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public SchedulePlan getById(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        return plan;
    }

    public List<SchedulePlanItem> getPlanItems(Long planId) {
        List<SchedulePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, planId)
                        .orderByAsc(SchedulePlanItem::getWeekday)
                        .orderByAsc(SchedulePlanItem::getStartPeriod));
        fillItemRelations(items);
        return items;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if (!"DRAFT".equals(plan.getStatus())) {
            throw new BusinessException("只能删除草稿方案");
        }
        // 先删除方案明细
        planItemMapper.delete(new LambdaQueryWrapper<SchedulePlanItem>().eq(SchedulePlanItem::getPlanId, id));
        // 再删除方案
        planMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void abandon(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        plan.setStatus("ABANDONED");
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    private void fillItemRelations(List<SchedulePlanItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<Long, Course> courseMap = courseMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getCourseId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getTeacherId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        Map<Long, ClassInfo> classMap = classInfoMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getClassId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));
        Map<Long, Classroom> roomMap = classroomMapper.selectBatchIds(items.stream()
                        .map(SchedulePlanItem::getClassroomId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));

        for (SchedulePlanItem item : items) {
            Course course = courseMap.get(item.getCourseId());
            Teacher teacher = teacherMap.get(item.getTeacherId());
            ClassInfo classInfo = classMap.get(item.getClassId());
            Classroom room = roomMap.get(item.getClassroomId());
            item.setCourseName(course != null ? course.getCourseName() : null);
            item.setTeacherName(teacher != null ? teacher.getName() : null);
            item.setClassName(classInfo != null ? classInfo.getClassName() : null);
            item.setRoomName(room != null ? room.getRoomName() : null);
            item.setTimeLabel("周" + item.getWeekday() + " 第" + item.getStartPeriod() + "-" + item.getEndPeriod() + "节");
        }
    }
}
