package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V4ScheduleRiskService;
import com.paike.scheduler.service.vo.RefreshPlanRisksVo;
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v4/schedule-risks")
@RequiredArgsConstructor
public class ScheduleRiskController {

    private final V4ScheduleRiskService scheduleRiskService;

    @GetMapping("/plans/{planId}")
    public Result<ScheduleRiskListVo> getPlanRisks(
            @PathVariable Long planId,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean onlyUnresolved
    ) {
        return Result.success(scheduleRiskService.getPlanRisks(planId, riskType, level, onlyUnresolved));
    }

    @PostMapping("/plans/{planId}/refresh")
    public Result<RefreshPlanRisksVo> refreshPlanRisks(@PathVariable Long planId) {
        ScheduleRiskListVo riskList = scheduleRiskService.getPlanRisks(planId, null, null, null);
        RefreshPlanRisksVo result = new RefreshPlanRisksVo(planId, riskList.getRiskCount(), "风险诊断已刷新");
        return Result.success("风险诊断已刷新", result);
    }
}
