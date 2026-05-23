package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleLockedItem;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ScheduleLockGuardService {

    private static final String TARGET_PLAN = "PLAN";
    private static final String TARGET_SCHEDULE = "SCHEDULE";

    private final ScheduleLockedItemMapper lockedItemMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final TimeSlotMapper timeSlotMapper;

    public void ensurePlanItemUnlocked(Long planItemId, String message) {
        if (planItemId == null) {
            return;
        }
        Long count = lockedItemMapper.selectCount(new LambdaQueryWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getTargetType, TARGET_PLAN)
                .eq(ScheduleLockedItem::getPlanItemId, planItemId)
                .eq(ScheduleLockedItem::getActiveFlag, 1));
        if (count != null && count > 0) {
            throw new BusinessException(message);
        }
    }

    public void ensureScheduleUnlocked(Long scheduleId, String message) {
        if (scheduleId == null) {
            return;
        }
        Long count = lockedItemMapper.selectCount(new LambdaQueryWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getTargetType, TARGET_SCHEDULE)
                .eq(ScheduleLockedItem::getScheduleId, scheduleId)
                .eq(ScheduleLockedItem::getActiveFlag, 1));
        if (count != null && count > 0) {
            throw new BusinessException(message);
        }
    }

    public void ensureScheduleAndLinkedPlanUnlocked(Schedule schedule, String message) {
        if (schedule == null) {
            return;
        }
        ensureScheduleUnlocked(schedule.getId(), message);
        SchedulePlanItem linkedItem = matchPlanItem(schedule);
        if (linkedItem != null) {
            ensurePlanItemUnlocked(linkedItem.getId(), message);
        }
    }

    private SchedulePlanItem matchPlanItem(Schedule schedule) {
        if (schedule.getPlanId() == null || schedule.getTeachingTaskId() == null) {
            return null;
        }
        List<SchedulePlanItem> items = planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, schedule.getPlanId())
                .eq(SchedulePlanItem::getTeachingTaskId, schedule.getTeachingTaskId()));
        if (items.isEmpty()) {
            return null;
        }
        TimeSlot slot = schedule.getTimeSlotId() == null ? null : timeSlotMapper.selectById(schedule.getTimeSlotId());
        if (slot == null) {
            return items.size() == 1 ? items.get(0) : null;
        }
        // 与 V4 调整链路保持同一口径：当前一个大节固定映射为两个连续小节。
        Integer weekday = slot.getDayOfWeek();
        Integer startPeriod = slot.getPeriodNo() == null ? null : slot.getPeriodNo() * 2 - 1;
        Integer endPeriod = startPeriod == null ? null : startPeriod + 1;
        if (weekday != null && startPeriod != null && endPeriod != null) {
            List<SchedulePlanItem> exactMatches = items.stream()
                    .filter(item -> Objects.equals(item.getWeekday(), weekday)
                            && Objects.equals(item.getStartPeriod(), startPeriod)
                            && Objects.equals(item.getEndPeriod(), endPeriod))
                    .sorted(Comparator.comparing(item -> Objects.equals(item.getClassroomId(), schedule.getClassroomId()) ? 0 : 1))
                    .toList();
            if (!exactMatches.isEmpty()) {
                return exactMatches.get(0);
            }
        }
        return items.size() == 1 ? items.get(0) : null;
    }
}
