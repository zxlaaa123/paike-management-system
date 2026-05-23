package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V5RepairSuggestionService;
import com.paike.scheduler.service.V5SimulationService;
import com.paike.scheduler.service.dto.V5RepairSuggestionGenerateRequest;
import com.paike.scheduler.service.vo.V5RepairSuggestionVo;
import com.paike.scheduler.service.vo.V5SimulationPlanDetailVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/repair-tasks/{taskId}/suggestions")
@RequiredArgsConstructor
public class V5RepairSuggestionController {

    private final V5RepairSuggestionService repairSuggestionService;
    private final V5SimulationService simulationService;

    @PostMapping("/generate")
    public Result<List<V5RepairSuggestionVo>> generate(
            @PathVariable Long taskId,
            @Valid @RequestBody(required = false) V5RepairSuggestionGenerateRequest request
    ) {
        return Result.success("修复建议已生成", repairSuggestionService.generate(taskId, request));
    }

    @GetMapping
    public Result<List<V5RepairSuggestionVo>> list(@PathVariable Long taskId) {
        return Result.success(repairSuggestionService.listByTask(taskId));
    }

    @GetMapping("/{suggestionId}")
    public Result<V5RepairSuggestionVo> detail(@PathVariable Long taskId, @PathVariable Long suggestionId) {
        return Result.success(repairSuggestionService.detail(taskId, suggestionId));
    }

    @PostMapping("/{suggestionId}/simulate")
    public Result<V5SimulationPlanDetailVo> simulate(@PathVariable Long taskId, @PathVariable Long suggestionId) {
        return Result.success("试算方案已生成", simulationService.generate(taskId, suggestionId));
    }
}
