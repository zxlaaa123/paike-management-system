package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleScoreReport;
import com.paike.scheduler.service.ScheduleScoreReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/schedule-score")
@RequiredArgsConstructor
public class ScheduleScoreReportController {

    private final ScheduleScoreReportService scheduleScoreReportService;

    @PostMapping("/generate")
    public Result<ScheduleScoreReportService.ScoreResult> generate(
            @RequestParam(required = false) Long semesterId
    ) {
        return Result.success(scheduleScoreReportService.generate(semesterId));
    }

    @GetMapping("/latest")
    public Result<ScheduleScoreReport> latest(
            @RequestParam(required = false) Long semesterId
    ) {
        return Result.success(scheduleScoreReportService.getLatest(semesterId));
    }

    @GetMapping("/reports")
    public Result<Page<ScheduleScoreReport>> list(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            @RequestParam(defaultValue = "1") int page,
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(scheduleScoreReportService.list(semesterId, grade, startTime, endTime, page, size));
    }
}
