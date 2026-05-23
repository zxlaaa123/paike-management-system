package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V5ConsistencyCheckService;
import com.paike.scheduler.service.V5RepairExplanationService;
import com.paike.scheduler.service.V5SimulationService;
import com.paike.scheduler.service.dto.V5LocalReplanRequest;
import com.paike.scheduler.service.vo.V5ConsistencyCheckReportVo;
import com.paike.scheduler.service.vo.V5RepairExplanationVo;
import com.paike.scheduler.service.vo.V5SimulationPlanDetailVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v5/repair-tasks/{taskId}/simulations")
@RequiredArgsConstructor
public class V5SimulationController {

    private final V5SimulationService simulationService;
    private final V5ConsistencyCheckService consistencyCheckService;
    private final V5RepairExplanationService repairExplanationService;

    @PostMapping("/local-replan")
    public Result<V5SimulationPlanDetailVo> localReplan(@PathVariable Long taskId, @Valid @RequestBody(required = false) V5LocalReplanRequest request) {
        return Result.success("局部重排试算方案已生成", simulationService.localReplan(taskId, request));
    }

    @GetMapping("/{planId}")
    public Result<V5SimulationPlanDetailVo> detail(@PathVariable Long taskId, @PathVariable Long planId) {
        return Result.success(simulationService.detail(taskId, planId));
    }

    @PostMapping("/{planId}/confirm")
    public Result<V5SimulationPlanDetailVo> confirm(@PathVariable Long taskId, @PathVariable Long planId) {
        return Result.success("试算方案已确认", simulationService.confirm(taskId, planId));
    }

    @PostMapping("/{planId}/apply")
    public Result<Map<String, Object>> apply(@PathVariable Long taskId, @PathVariable Long planId) {
        return Result.success("试算方案已应用", simulationService.apply(taskId, planId));
    }

    @PostMapping("/{planId}/discard")
    public Result<V5SimulationPlanDetailVo> discard(@PathVariable Long taskId, @PathVariable Long planId) {
        return Result.success("试算方案已放弃", simulationService.discard(taskId, planId));
    }

    @PostMapping("/{planId}/consistency-check")
    public Result<V5ConsistencyCheckReportVo> runConsistencyCheck(@PathVariable Long taskId, @PathVariable Long planId) {
        return Result.success("一致性校验完成", consistencyCheckService.check(taskId, planId, true));
    }

    @GetMapping("/{planId}/consistency-check/latest")
    public Result<V5ConsistencyCheckReportVo> latestConsistencyCheck(@PathVariable Long taskId, @PathVariable Long planId) {
        return Result.success(consistencyCheckService.latest(taskId, planId));
    }

    @PostMapping("/{planId}/ai-explanation")
    public Result<V5RepairExplanationVo> generateExplanation(@PathVariable Long taskId, @PathVariable Long planId) {
        return Result.success("AI 修复解释已生成", repairExplanationService.generate(taskId, planId));
    }
}
