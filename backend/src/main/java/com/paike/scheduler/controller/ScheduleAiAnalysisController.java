package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V4ScheduleAiAnalysisService;
import com.paike.scheduler.service.dto.V4ScheduleAiAnalysisRequest;
import com.paike.scheduler.service.vo.ScheduleAiAnalysisVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v4/ai/schedule-analysis")
@RequiredArgsConstructor
public class ScheduleAiAnalysisController {

    private final V4ScheduleAiAnalysisService scheduleAiAnalysisService;

    @PostMapping("/plans/{planId}")
    public Result<ScheduleAiAnalysisVo> generatePlanAiAnalysis(
            @PathVariable Long planId,
            @RequestBody(required = false) V4ScheduleAiAnalysisRequest request
    ) {
        return Result.success(scheduleAiAnalysisService.generateAnalysis(planId, request));
    }
}

