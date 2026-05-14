package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleScoreDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleScoreService {

    private final ScheduleScoreDetailMapper scoreDetailMapper;
    private final SchedulePlanMapper planMapper;
    private final ScheduleRuleWeightService ruleWeightService;

    public List<ScheduleScoreDetail> getScoreDetails(Long planId) {
        return scoreDetailMapper.selectList(
                new LambdaQueryWrapper<ScheduleScoreDetail>()
                        .eq(ScheduleScoreDetail::getPlanId, planId)
                        .orderByAsc(ScheduleScoreDetail::getRuleCode));
    }

    public BigDecimal getScoreSummary(Long planId) {
        List<ScheduleScoreDetail> details = getScoreDetails(planId);
        BigDecimal total = BigDecimal.ZERO;
        for (ScheduleScoreDetail d : details) {
            total = total.add(d.getScore() != null ? d.getScore() : BigDecimal.ZERO);
        }
        return normalizeTotalScore(total);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rescore(SchedulePlan plan) {
        // 1. 删除旧评分明细
        scoreDetailMapper.delete(
                new LambdaQueryWrapper<ScheduleScoreDetail>()
                        .eq(ScheduleScoreDetail::getPlanId, plan.getId()));

        // 2. 加载规则权重
        List<ScheduleRuleWeight> rules = ruleWeightService.list(
                plan.getSemesterId(), plan.getStrategyType(), null);
        if (rules.isEmpty()) {
            ruleWeightService.initDefaultRules(plan.getSemesterId(), plan.getStrategyType());
            rules = ruleWeightService.list(plan.getSemesterId(), plan.getStrategyType(), null);
        }

        // 3. 计算各项评分
        List<ScheduleScoreDetail> details = new ArrayList<>();
        BigDecimal totalScore = BigDecimal.ZERO;

        for (ScheduleRuleWeight rule : rules) {
            if (rule.getEnabled() == 0) continue;

            ScheduleScoreDetail detail = new ScheduleScoreDetail();
            detail.setPlanId(plan.getId());
            detail.setSemesterId(plan.getSemesterId());
            detail.setRuleCode(rule.getRuleCode());
            detail.setRuleType(rule.getRuleType());
            detail.setRuleName(rule.getRuleName());

            BigDecimal score = calculateItemScore(rule, plan);
            detail.setScore(score);
            detail.setMaxScore(BigDecimal.ZERO);
            detail.setViolationCount(0);
            detail.setDetailMessage(generateDetailMessage(rule, score));

            details.add(detail);
            totalScore = totalScore.add(score);
        }

        // 4. 限制总分在 0-100 之间
        totalScore = normalizeTotalScore(totalScore);

        // 5. 保存评分明细
        for (ScheduleScoreDetail detail : details) {
            scoreDetailMapper.insert(detail);
        }

        // 6. 更新方案总分（持久化到数据库）
        plan.setTotalScore(totalScore);
        planMapper.updateById(plan);
    }

    private BigDecimal normalizeTotalScore(BigDecimal totalScore) {
        if (totalScore == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (totalScore.compareTo(BigDecimal.ZERO) < 0) {
            totalScore = BigDecimal.ZERO;
        }
        if (totalScore.compareTo(new BigDecimal("100")) > 0) {
            totalScore = new BigDecimal("100");
        }
        return totalScore.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateItemScore(ScheduleRuleWeight rule, SchedulePlan plan) {
        BigDecimal weight = rule.getWeight() != null ? rule.getWeight() : BigDecimal.ONE;

        if ("HARD".equals(rule.getRuleType())) {
            if (plan.getConflictCount() != null && plan.getConflictCount() > 0) {
                return weight.negate();
            }
            return BigDecimal.ZERO;
        } else {
            if (plan.getUnscheduledCount() != null && plan.getUnscheduledCount() > 0) {
                BigDecimal penalty = weight.multiply(new BigDecimal(plan.getUnscheduledCount()))
                        .divide(new BigDecimal("10"), 2, RoundingMode.HALF_UP);
                return penalty.negate();
            }
            return BigDecimal.ZERO;
        }
    }

    private String generateDetailMessage(ScheduleRuleWeight rule, BigDecimal score) {
        if (score.compareTo(BigDecimal.ZERO) == 0) {
            return "无违规";
        }
        return rule.getRuleName() + "扣分：" + score + "分";
    }
}
