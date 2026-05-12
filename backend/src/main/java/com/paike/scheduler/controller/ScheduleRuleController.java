package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleRuleConfig;
import com.paike.scheduler.service.ScheduleRuleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedule-rules")
@RequiredArgsConstructor
public class ScheduleRuleController {

    private final ScheduleRuleService scheduleRuleService;

    @GetMapping
    public Result<List<ScheduleRuleConfig>> list() {
        return Result.success(scheduleRuleService.listAll());
    }

    @PutMapping
    public Result<Void> update(@RequestBody List<RuleUpdateForm> rules) {
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

    @Data
    public static class RuleUpdateForm {
        private String ruleKey;
        private String ruleValue;
        private Integer enabled;
    }
}
