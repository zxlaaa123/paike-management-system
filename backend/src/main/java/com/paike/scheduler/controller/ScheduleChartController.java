package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V4ScheduleChartService;
import com.paike.scheduler.service.vo.ScheduleClassDailyLoadChartVo;
import com.paike.scheduler.service.vo.ScheduleRoomUtilizationChartVo;
import com.paike.scheduler.service.vo.ScheduleScoreRadarChartVo;
import com.paike.scheduler.service.vo.ScheduleTeacherHoursChartVo;
import com.paike.scheduler.service.vo.ScheduleTimeDensityChartVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v4/schedule-charts")
@RequiredArgsConstructor
public class ScheduleChartController {

    private final V4ScheduleChartService scheduleChartService;

    @GetMapping("/plans/{planId}/teacher-hours")
    public Result<ScheduleTeacherHoursChartVo> getTeacherHours(@PathVariable Long planId) {
        return Result.success(scheduleChartService.getTeacherHours(planId));
    }

    @GetMapping("/plans/{planId}/room-utilization")
    public Result<ScheduleRoomUtilizationChartVo> getRoomUtilization(@PathVariable Long planId) {
        return Result.success(scheduleChartService.getRoomUtilization(planId));
    }

    @GetMapping("/plans/{planId}/class-daily-load")
    public Result<ScheduleClassDailyLoadChartVo> getClassDailyLoad(@PathVariable Long planId) {
        return Result.success(scheduleChartService.getClassDailyLoad(planId));
    }

    @GetMapping("/plans/{planId}/time-density")
    public Result<ScheduleTimeDensityChartVo> getTimeDensity(@PathVariable Long planId) {
        return Result.success(scheduleChartService.getTimeDensity(planId));
    }

    @GetMapping("/plans/{planId}/score-radar")
    public Result<ScheduleScoreRadarChartVo> getScoreRadar(@PathVariable Long planId) {
        return Result.success(scheduleChartService.getScoreRadar(planId));
    }
}
