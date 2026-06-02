package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.service.TimeSlotService;
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

    private final TimeSlotService timeSlotService;

    @GetMapping
    public Result<List<TimeSlot>> listAll() {
        return Result.success(timeSlotService.listAll());
    }

    @GetMapping("/{id}")
    public Result<TimeSlot> getById(@PathVariable Long id) {
        return Result.success(timeSlotService.getById(id));
    }

    @GetMapping("/day/{dayOfWeek}")
    public Result<List<TimeSlot>> listByDay(@PathVariable Integer dayOfWeek) {
        return Result.success(timeSlotService.listByDay(dayOfWeek));
    }
}
