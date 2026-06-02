package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.TimeSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TimeSlotService {

    private final TimeSlotMapper timeSlotMapper;

    public List<TimeSlot> listAll() {
        return timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
            .orderByAsc(TimeSlot::getSortOrder));
    }

    public TimeSlot getById(Long id) {
        TimeSlot slot = timeSlotMapper.selectById(id);
        if (slot == null) {
            throw new BusinessException(404, "时间段不存在");
        }
        return slot;
    }

    public List<TimeSlot> listByDay(Integer dayOfWeek) {
        return timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
            .eq(TimeSlot::getDayOfWeek, dayOfWeek)
            .orderByAsc(TimeSlot::getPeriodNo));
    }
}
