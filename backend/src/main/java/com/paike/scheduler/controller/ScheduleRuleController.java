package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleRuleConfig;
import com.paike.scheduler.service.ScheduleRuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedule-rules")
@RequiredArgsConstructor
@Validated
public class ScheduleRuleController {

    private final ScheduleRuleService scheduleRuleService;

    @GetMapping
    public Result<List<ScheduleRuleConfig>> list() {
        return Result.success(scheduleRuleService.listAll());
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody List<@Valid RuleUpdateForm> rules) {
        List<ScheduleRuleConfig> entities = rules.stream().map(form -> {
            ScheduleRuleConfig entity = new ScheduleRuleConfig();
            entity.setRuleKey(form.getRuleKey());
            entity.setRuleValue(form.getRuleValue());
            entity.setEnabled(form.getEnabled());
            return entity;
        }).toList();
        scheduleRuleService.updateRules(entities);
        return Result.success("保存成功", null);
    }

    @PostMapping("/reset-default")
    public Result<Void> resetDefault() {
        scheduleRuleService.resetToDefault();
        return Result.success("已恢复默认配置", null);
    }

    @Getter
    public static class RuleUpdateForm {
        @NotBlank(message = "规则键不能为空")
        @Size(max = 100, message = "规则键最长 100 字符")
        private String ruleKey;

        @NotBlank(message = "规则值不能为空")
        @Size(max = 100, message = "规则值最长 100 字符")
        private String ruleValue;

        private Integer enabled;
    }
}
