package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.PerformanceBaselineRecord;
import com.paike.scheduler.service.PerformanceBaselineService;
import com.paike.scheduler.service.vo.PerformanceSummaryVo;
import com.paike.scheduler.service.vo.PerformanceTrendVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v6/performance")
@RequiredArgsConstructor
public class PerformanceBaselineController {

    private final PerformanceBaselineService performanceBaselineService;

    @GetMapping("/baselines")
    public Result<Page<PerformanceBaselineRecord>> list(
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Boolean success,
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            @RequestParam(defaultValue = "1") int page,
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(performanceBaselineService.list(operationType, semesterId, planId, success, page, size));
    }

    @GetMapping("/summary")
    public Result<List<PerformanceSummaryVo>> summary() {
        return Result.success(performanceBaselineService.summary());
    }

    @GetMapping("/trends")
    public Result<List<PerformanceTrendVo>> trends(
            @RequestParam(required = false) String operationType,
            @jakarta.validation.constraints.Min(value = 1, message = "数量必须大于0")
            @jakarta.validation.constraints.Max(value = 100, message = "数量不能超过100")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return Result.success(performanceBaselineService.trends(operationType, limit));
    }
}
