package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
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
    private final ScheduleMapper scheduleMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final TimeSlotMapper timeSlotMapper;

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

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyPlan(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if ("ABANDONED".equals(plan.getStatus())) {
            throw new BusinessException("已废弃方案不能应用");
        }
        if (plan.getScheduledCount() == null || plan.getScheduledCount() == 0) {
            throw new BusinessException("该方案没有排课明细，无法应用");
        }

        Long semesterId = plan.getSemesterId();

        // 1. 将同一学期下旧的已应用方案的 schedule 记录软删除（保留历史方案记录本身）
        List<SchedulePlan> oldAppliedPlans = planMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SchedulePlan>()
                        .eq(SchedulePlan::getSemesterId, semesterId)
                        .eq(SchedulePlan::getStatus, "APPLIED"));
        for (SchedulePlan oldPlan : oldAppliedPlans) {
            // 软删除旧的正式课表记录
            scheduleMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Schedule>()
                            .eq(Schedule::getSemesterId, semesterId)
                            .eq(Schedule::getPlanId, oldPlan.getId())
                            .set(Schedule::getDeleted, 1)
                            .set(Schedule::getUpdateTime, LocalDateTime.now()));
            // 旧方案状态改为 DRAFT（保留历史记录，不删除）
            oldPlan.setStatus("DRAFT");
            oldPlan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(oldPlan);
        }

        // 2. 将方案明细写入正式课表
        List<SchedulePlanItem> items = planItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, id));

        // 预加载所有 TimeSlot，构建 (dayOfWeek, periodNo) -> timeSlotId 映射
        Map<String, Long> timeSlotMap = timeSlotMapper.selectList(null).stream()
                .collect(Collectors.toMap(
                        ts -> ts.getDayOfWeek() + "_" + ts.getPeriodNo(),
                        TimeSlot::getId,
                        (a, b) -> a));

        int insertedCount = 0;
        for (SchedulePlanItem item : items) {
            // 将小节号转换为大节号：1->1, 3->2, 5->3, 7->4
            int periodNo = (item.getStartPeriod() + 1) / 2;
            String key = item.getWeekday() + "_" + periodNo;
            Long timeSlotId = timeSlotMap.get(key);
            if (timeSlotId == null) {
                throw new BusinessException("无法找到对应的时间段：周" + item.getWeekday() + " 第" + item.getStartPeriod() + "-" + item.getEndPeriod() + "节");
            }

            Schedule schedule = new Schedule();
            schedule.setSemesterId(semesterId);
            schedule.setPlanId(plan.getId());
            schedule.setTeachingTaskId(item.getTeachingTaskId());
            schedule.setCourseId(item.getCourseId());
            schedule.setTeacherId(item.getTeacherId());
            schedule.setClassId(item.getClassId());
            schedule.setClassroomId(item.getClassroomId());
            schedule.setTimeSlotId(timeSlotId);
            schedule.setSourceType("PLAN");
            schedule.setDeleted(0);
            schedule.setCreateTime(LocalDateTime.now());
            schedule.setUpdateTime(LocalDateTime.now());
            scheduleMapper.insert(schedule);
            insertedCount++;
        }

        // 3. 更新方案状态为已应用
        plan.setStatus("APPLIED");
        plan.setAppliedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);

        return Map.of(
                "planId", plan.getId(),
                "semesterId", semesterId,
                "appliedCount", insertedCount,
                "appliedAt", plan.getAppliedAt()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rollbackPlan(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if ("ABANDONED".equals(plan.getStatus())) {
            throw new BusinessException("已废弃方案不能回滚应用");
        }
        if (plan.getScheduledCount() == null || plan.getScheduledCount() == 0) {
            throw new BusinessException("该方案没有排课明细，无法应用");
        }

        // 回滚本质上是重新应用历史方案，复用 applyPlan 逻辑
        return applyPlan(id);
    }
}
