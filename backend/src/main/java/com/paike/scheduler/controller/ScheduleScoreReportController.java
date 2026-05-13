package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleScoreReport;
import com.paike.scheduler.service.ScheduleScoreReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/schedule-score")
@RequiredArgsConstructor
public class ScheduleScoreReportController {

    private final ScheduleScoreReportService scheduleScoreReportService;

    @PostMapping("/generate")
    public Result<ScheduleScoreReportService.ScoreResult> generate() {
        return Result.success(scheduleScoreReportService.generate());
    }

    @GetMapping("/latest")
    public Result<ScheduleScoreReport> latest() {
        return Result.success(scheduleScoreReportService.getLatest());
    }

    @GetMapping("/reports")
    public Result<Page<ScheduleScoreReport>> list(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(scheduleScoreReportService.list(grade, startTime, endTime, page, size));
    }
}
