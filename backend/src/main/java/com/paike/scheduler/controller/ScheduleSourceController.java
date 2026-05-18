package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V4ScheduleSourceService;
import com.paike.scheduler.service.vo.ScheduleAdjustmentLogListVo;
import com.paike.scheduler.service.vo.ScheduleCurrentSourceVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v4")
@RequiredArgsConstructor
public class ScheduleSourceController {

    private final V4ScheduleSourceService scheduleSourceService;

    @GetMapping("/schedules/current/source")
    public Result<ScheduleCurrentSourceVo> getCurrentSource(@RequestParam(required = false) Long termId) {
        return Result.success(scheduleSourceService.getCurrentSource(termId));
    }

    @GetMapping("/schedule-adjustments/plans/{planId}/logs")
    public Result<ScheduleAdjustmentLogListVo> getPlanAdjustmentLogs(@PathVariable Long planId) {
        return Result.success(scheduleSourceService.getPlanAdjustmentLogs(planId));
    }
}
