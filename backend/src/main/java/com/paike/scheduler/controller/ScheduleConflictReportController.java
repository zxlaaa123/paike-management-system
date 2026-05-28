package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleConflictReport;
import com.paike.scheduler.service.ScheduleConflictReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/schedule-conflict-reports")
@RequiredArgsConstructor
public class ScheduleConflictReportController {

    private final ScheduleConflictReportService scheduleConflictReportService;

    @PostMapping("/generate")
    public Result<ScheduleConflictReportService.GenerateResult> generate(
            @RequestParam(required = false) Long semesterId
    ) {
        return Result.success(scheduleConflictReportService.generate(semesterId));
    }

    @GetMapping
    public Result<Page<ScheduleConflictReport>> list(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) String reportNo,
            @RequestParam(required = false) String conflictType,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectName,
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            @RequestParam(defaultValue = "1") int page,
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(scheduleConflictReportService.list(semesterId, reportNo, conflictType, objectType, objectName, page, size));
    }

    @DeleteMapping
    public Result<Void> clear(@RequestParam String reportNo) {
        scheduleConflictReportService.clear(reportNo);
        return Result.success("清空成功", null);
    }
}
