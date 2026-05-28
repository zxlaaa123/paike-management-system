package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.TimeSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/time-slots")
@RequiredArgsConstructor
public class TimeSlotController {

    private final TimeSlotMapper timeSlotMapper;

    @GetMapping
    public Result<List<TimeSlot>> listAll() {
        List<TimeSlot> list = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
            .orderByAsc(TimeSlot::getSortOrder));
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<TimeSlot> getById(@PathVariable Long id) {
        TimeSlot slot = timeSlotMapper.selectById(id);
        if (slot == null) {
            throw new BusinessException(404, "时间段不存在");
        }
        return Result.success(slot);
    }

    @GetMapping("/day/{dayOfWeek}")
    public Result<List<TimeSlot>> listByDay(@PathVariable Integer dayOfWeek) {
        List<TimeSlot> list = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
            .eq(TimeSlot::getDayOfWeek, dayOfWeek)
            .orderByAsc(TimeSlot::getPeriodNo));
        return Result.success(list);
    }
}
