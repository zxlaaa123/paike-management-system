package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V4ScheduleAnalysisService;
import com.paike.scheduler.service.vo.RefreshPlanSummaryVo;
import com.paike.scheduler.service.vo.ScheduleAnalysisSummaryVo;
import com.paike.scheduler.service.vo.ScheduleScoreDetailExplainVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v4/schedule-analysis")
@RequiredArgsConstructor
public class ScheduleAnalysisController {

    private final V4ScheduleAnalysisService scheduleAnalysisService;

    @GetMapping("/plans/{planId}/summary")
    public Result<ScheduleAnalysisSummaryVo> getPlanSummary(@PathVariable Long planId) {
        return Result.success(scheduleAnalysisService.getPlanSummary(planId));
    }

    @GetMapping("/plans/{planId}/score-details")
    public Result<ScheduleScoreDetailExplainVo> getPlanScoreDetails(@PathVariable Long planId) {
        return Result.success(scheduleAnalysisService.getPlanScoreDetails(planId));
    }

    @PostMapping("/plans/{planId}/refresh")
    public Result<RefreshPlanSummaryVo> refreshPlanSummary(@PathVariable Long planId) {
        scheduleAnalysisService.getPlanSummary(planId);
        RefreshPlanSummaryVo result = new RefreshPlanSummaryVo(planId, true, "方案质量分析已刷新");
        return Result.success("方案质量分析已刷新", result);
    }
}
