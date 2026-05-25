package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.service.ScheduleRuleWeightService;
import com.paike.scheduler.service.SemesterService;
import com.paike.scheduler.service.dto.ScheduleRuleWeightBatchForm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v3/schedule-rule-weights")
@RequiredArgsConstructor
public class ScheduleRuleWeightController {

    private final ScheduleRuleWeightService ruleWeightService;
    private final SemesterService semesterService;

    @GetMapping
    public Result<List<ScheduleRuleWeight>> list(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) String strategyType,
            @RequestParam(required = false) String ruleType
    ) {
        Long resolvedSemesterId = semesterId;
        if (resolvedSemesterId == null) {
            resolvedSemesterId = semesterService.getCurrentSemester().getId();
        }
        return Result.success(ruleWeightService.list(resolvedSemesterId, strategyType, ruleType));
    }

    @PostMapping("/init-default")
    public Result<Void> initDefault(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(defaultValue = "COMPREHENSIVE") String strategyType
    ) {
        Long resolvedSemesterId = semesterId;
        if (resolvedSemesterId == null) {
            resolvedSemesterId = semesterService.getCurrentSemester().getId();
        }
        ruleWeightService.initDefaultRules(resolvedSemesterId, strategyType);
        return Result.success("默认规则权重初始化成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody WeightUpdateRequest request) {
        ruleWeightService.updateWeight(id, request.getWeight(), request.getEnabled(), request.getDescription());
        return Result.success("修改成功", null);
    }

    @PutMapping("/batch")
    public Result<Void> batchUpdate(@Valid @RequestBody ScheduleRuleWeightBatchForm form) {
        ruleWeightService.batchUpdate(form.getRules());
        return Result.success("批量保存成功", null);
    }

    @Data
    public static class WeightUpdateRequest {
        private BigDecimal weight;
        private Integer enabled;

        @Size(max = 500, message = "规则说明最长 500 字符")
        private String description;
    }
}
