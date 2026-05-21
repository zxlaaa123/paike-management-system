package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleConflictReport;
import com.paike.scheduler.service.ScheduleConflictReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedule-conflict-reports")
@RequiredArgsConstructor
public class ScheduleConflictReportController {

    private final ScheduleConflictReportService scheduleConflictReportService;

    @PostMapping("/generate")
    public Result<ScheduleConflictReportService.GenerateResult> generate() {
        return Result.success(scheduleConflictReportService.generate());
    }

    @GetMapping
    public Result<Page<ScheduleConflictReport>> list(
            @RequestParam(required = false) String reportNo,
            @RequestParam(required = false) String conflictType,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(scheduleConflictReportService.list(reportNo, conflictType, objectType, objectName, page, size));
    }

    @DeleteMapping
    public Result<Void> clear(@RequestParam String reportNo) {
        scheduleConflictReportService.clear(reportNo);
        return Result.success("清空成功", null);
    }
}
