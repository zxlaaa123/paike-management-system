package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleAdjustLog;
import com.paike.scheduler.service.SchedulePlanExplainService;
import com.paike.scheduler.service.SemesterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v3/schedule-adjust-logs")
@RequiredArgsConstructor
public class ScheduleAdjustLogController {

    private final SchedulePlanExplainService explainService;
    private final SemesterService semesterService;

    @GetMapping
    public Result<Page<ScheduleAdjustLog>> list(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Long teachingTaskId,
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            @RequestParam(defaultValue = "1") int pageNum,
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Long resolvedSemesterId = semesterId;
        if (resolvedSemesterId == null) {
            resolvedSemesterId = semesterService.getCurrentSemester().getId();
        }
        return Result.success(explainService.listAdjustLogs(resolvedSemesterId, planId, teachingTaskId, pageNum, pageSize));
    }
}
