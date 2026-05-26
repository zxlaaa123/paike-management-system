package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.mapper.ScheduleRuleWeightMapper;
import com.paike.scheduler.service.dto.ScheduleRuleWeightBatchForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleRuleWeightService {

    private final ScheduleRuleWeightMapper ruleWeightMapper;

    public List<ScheduleRuleWeight> list(Long semesterId, String strategyType, String ruleType) {
        LambdaQueryWrapper<ScheduleRuleWeight> wrapper = new LambdaQueryWrapper<ScheduleRuleWeight>()
                .eq(ScheduleRuleWeight::getSemesterId, semesterId);
        if (strategyType != null && !strategyType.isBlank()) {
            wrapper.eq(ScheduleRuleWeight::getStrategyType, strategyType);
        }
        if (ruleType != null && !ruleType.isBlank()) {
            wrapper.eq(ScheduleRuleWeight::getRuleType, ruleType);
        }
        wrapper.orderByAsc(ScheduleRuleWeight::getRuleType).orderByAsc(ScheduleRuleWeight::getRuleCode);
        return ruleWeightMapper.selectList(wrapper);
    }

    public ScheduleRuleWeight getById(Long id) {
        return ruleWeightMapper.selectById(id);
    }

    public void updateWeight(Long id, java.math.BigDecimal weight, Integer enabled, String description) {
        ScheduleRuleWeight rule = ruleWeightMapper.selectById(id);
        if (rule == null) throw new BusinessException("规则不存在");
        if (enabled != null && enabled == 0 && isHardRule(rule)) {
            throw new BusinessException("硬约束规则不能关闭：" + rule.getRuleCode());
        }
        if (weight != null) rule.setWeight(weight);
        if (enabled != null) rule.setEnabled(enabled);
        if (description != null) rule.setDescription(description);
        ruleWeightMapper.updateById(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(List<ScheduleRuleWeightBatchForm.Item> rules) {
        for (ScheduleRuleWeightBatchForm.Item rule : rules) {
            ScheduleRuleWeight existing = ruleWeightMapper.selectById(rule.getId());
            if (existing != null) {
                if (rule.getEnabled() != null && rule.getEnabled() == 0 && isHardRule(existing)) {
                    throw new BusinessException("硬约束规则不能关闭：" + existing.getRuleCode());
                }
                existing.setWeight(rule.getWeight());
                existing.setEnabled(rule.getEnabled());
                existing.setDescription(rule.getDescription());
                existing.setUpdatedAt(LocalDateTime.now());
                ruleWeightMapper.updateById(existing);
            }
        }
    }

    public boolean hasRules(Long semesterId, String strategyType) {
        return ruleWeightMapper.selectCount(
                new LambdaQueryWrapper<ScheduleRuleWeight>()
                        .eq(ScheduleRuleWeight::getSemesterId, semesterId)
                        .eq(ScheduleRuleWeight::getStrategyType, strategyType)) > 0;
    }

    public void initDefaultRules(Long semesterId, String strategyType) {
        if (hasRules(semesterId, strategyType)) return;

        List<ScheduleRuleWeight> defaults = getDefaultRules(semesterId, strategyType);
        for (ScheduleRuleWeight rule : defaults) {
            ruleWeightMapper.insert(rule);
        }
    }

    private List<ScheduleRuleWeight> getDefaultRules(Long semesterId, String strategyType) {
        java.util.List<ScheduleRuleWeight> rules = new java.util.ArrayList<>();

        switch (strategyType) {
            case "TEACHER_PRIORITY":
                addRule(rules, semesterId, strategyType, "TEACHER_UNAVAILABLE", "教师禁排时间", "HARD", new java.math.BigDecimal("100"), "教师禁排时间不能安排课程");
                addRule(rules, semesterId, strategyType, "TEACHER_TIME_CONFLICT", "教师时间冲突", "HARD", new java.math.BigDecimal("100"), "同一教师同一时间不能上两门课");
                addRule(rules, semesterId, strategyType, "TEACHER_DAILY_LOAD", "教师每日负载", "SOFT", new java.math.BigDecimal("50"), "教师每天上课数量尽量合理");
                addRule(rules, semesterId, strategyType, "CONTINUOUS_PERIOD_LIMIT", "连续上课限制", "SOFT", new java.math.BigDecimal("45"), "连续上课节次不宜过长");
                addRule(rules, semesterId, strategyType, "CLASS_DAILY_BALANCE", "班级每日均衡", "SOFT", new java.math.BigDecimal("20"), "班级每天课程数量尽量均衡");
                addRule(rules, semesterId, strategyType, "CLASSROOM_UTILIZATION", "教室利用率", "SOFT", new java.math.BigDecimal("10"), "尽量提高教室使用率");
                break;
            case "CLASS_BALANCE":
                addRule(rules, semesterId, strategyType, "CLASS_TIME_CONFLICT", "班级时间冲突", "HARD", new java.math.BigDecimal("100"), "同一班级同一时间不能上两门课");
                addRule(rules, semesterId, strategyType, "CLASS_DAILY_BALANCE", "班级每日均衡", "SOFT", new java.math.BigDecimal("50"), "班级每天课程数量尽量均衡");
                addRule(rules, semesterId, strategyType, "COURSE_DISTRIBUTION", "课程分布均衡", "SOFT", new java.math.BigDecimal("45"), "同一课程不要过度集中");
                addRule(rules, semesterId, strategyType, "CONTINUOUS_PERIOD_LIMIT", "连续上课限制", "SOFT", new java.math.BigDecimal("40"), "连续上课节次不宜过长");
                addRule(rules, semesterId, strategyType, "MORNING_THEORY_PRIORITY", "理论课优先上午", "SOFT", new java.math.BigDecimal("25"), "理论课尽量安排在上午");
                addRule(rules, semesterId, strategyType, "TEACHER_DAILY_LOAD", "教师每日负载", "SOFT", new java.math.BigDecimal("20"), "教师每天上课数量尽量合理");
                addRule(rules, semesterId, strategyType, "CLASSROOM_UTILIZATION", "教室利用率", "SOFT", new java.math.BigDecimal("10"), "尽量提高教室使用率");
                break;
            case "CLASSROOM_UTILIZATION":
                addRule(rules, semesterId, strategyType, "CLASSROOM_TIME_CONFLICT", "教室时间冲突", "HARD", new java.math.BigDecimal("100"), "同一教室同一时间不能安排两门课");
                addRule(rules, semesterId, strategyType, "CLASSROOM_CAPACITY", "教室容量不足", "HARD", new java.math.BigDecimal("90"), "教室容量必须满足班级人数");
                addRule(rules, semesterId, strategyType, "CLASSROOM_TYPE_MISMATCH", "教室类型不匹配", "HARD", new java.math.BigDecimal("90"), "课程类型应匹配教室类型");
                addRule(rules, semesterId, strategyType, "CLASSROOM_UTILIZATION", "教室利用率", "SOFT", new java.math.BigDecimal("60"), "尽量提高教室使用率");
                addRule(rules, semesterId, strategyType, "CLASS_DAILY_BALANCE", "班级每日均衡", "SOFT", new java.math.BigDecimal("20"), "班级每天课程数量尽量均衡");
                addRule(rules, semesterId, strategyType, "TEACHER_DAILY_LOAD", "教师每日负载", "SOFT", new java.math.BigDecimal("20"), "教师每天上课数量尽量合理");
                break;
            default: // COMPREHENSIVE
                addRule(rules, semesterId, strategyType, "TEACHER_TIME_CONFLICT", "教师时间冲突", "HARD", new java.math.BigDecimal("100"), "同一教师同一时间不能上两门课");
                addRule(rules, semesterId, strategyType, "CLASS_TIME_CONFLICT", "班级时间冲突", "HARD", new java.math.BigDecimal("100"), "同一班级同一时间不能上两门课");
                addRule(rules, semesterId, strategyType, "CLASSROOM_TIME_CONFLICT", "教室时间冲突", "HARD", new java.math.BigDecimal("100"), "同一教室同一时间不能安排两门课");
                addRule(rules, semesterId, strategyType, "TEACHER_UNAVAILABLE", "教师禁排时间", "HARD", new java.math.BigDecimal("90"), "教师禁排时间不能安排课程");
                addRule(rules, semesterId, strategyType, "CLASSROOM_CAPACITY", "教室容量不足", "HARD", new java.math.BigDecimal("80"), "教室容量必须满足班级人数");
                addRule(rules, semesterId, strategyType, "CLASSROOM_TYPE_MISMATCH", "教室类型不匹配", "HARD", new java.math.BigDecimal("80"), "课程类型应匹配教室类型");
                addRule(rules, semesterId, strategyType, "CLASS_DAILY_BALANCE", "班级每日均衡", "SOFT", new java.math.BigDecimal("30"), "班级每天课程数量尽量均衡");
                addRule(rules, semesterId, strategyType, "TEACHER_DAILY_LOAD", "教师每日负载", "SOFT", new java.math.BigDecimal("30"), "教师每天上课数量尽量合理");
                addRule(rules, semesterId, strategyType, "CONTINUOUS_PERIOD_LIMIT", "连续上课限制", "SOFT", new java.math.BigDecimal("25"), "连续上课节次不宜过长");
                addRule(rules, semesterId, strategyType, "COURSE_DISTRIBUTION", "课程分布均衡", "SOFT", new java.math.BigDecimal("25"), "同一课程不要过度集中");
                addRule(rules, semesterId, strategyType, "CLASSROOM_UTILIZATION", "教室利用率", "SOFT", new java.math.BigDecimal("20"), "尽量提高教室使用率");
                break;
        }
        return rules;
    }

    private void addRule(List<ScheduleRuleWeight> rules, Long semesterId, String strategyType,
                         String ruleCode, String ruleName, String ruleType, java.math.BigDecimal weight, String desc) {
        ScheduleRuleWeight rule = new ScheduleRuleWeight();
        rule.setSemesterId(semesterId);
        rule.setStrategyType(strategyType);
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleName);
        rule.setRuleType(ruleType);
        rule.setWeight(weight);
        rule.setEnabled(1);
        rule.setDescription(desc);
        rules.add(rule);
    }

    private boolean isHardRule(ScheduleRuleWeight rule) {
        if (rule == null || rule.getRuleCode() == null) {
            return false;
        }
        return switch (rule.getRuleCode()) {
            case "TEACHER_TIME_CONFLICT",
                    "CLASS_TIME_CONFLICT",
                    "CLASSROOM_TIME_CONFLICT",
                    "TEACHER_UNAVAILABLE",
                    "CLASSROOM_CAPACITY",
                    "CLASSROOM_TYPE_MISMATCH" -> true;
            default -> "HARD".equalsIgnoreCase(rule.getRuleType());
        };
    }
}
