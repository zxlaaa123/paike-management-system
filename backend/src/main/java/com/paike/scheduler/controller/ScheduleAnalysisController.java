package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V4ScheduleAnalysisService;
import com.paike.scheduler.service.vo.ScheduleAnalysisSummaryVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v4/schedule-analysis")
@RequiredArgsConstructor
public class ScheduleAnalysisController {

    private final V4ScheduleAnalysisService scheduleAnalysisService;

    @GetMapping("/plans/{planId}/summary")
    public Result<ScheduleAnalysisSummaryVo> getPlanSummary(@PathVariable Long planId) {
        return Result.success(scheduleAnalysisService.getPlanSummary(planId));
    }

    @PostMapping("/plans/{planId}/refresh")
    public Result<Map<String, Object>> refreshPlanSummary(@PathVariable Long planId) {
        scheduleAnalysisService.getPlanSummary(planId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("refreshed", true);
        result.put("message", "方案质量分析已刷新");
        return Result.success("方案质量分析已刷新", result);
    }
}
