package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.service.vo.ComparePlanVo;
import com.paike.scheduler.service.vo.CompareResultVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleCompareService {

    private final SchedulePlanMapper planMapper;
    private final ScheduleScoreService scoreService;

    public CompareResultVo compare(Long semesterId, List<Long> planIds) {
        if (planIds == null || planIds.size() < 2) {
            throw new BusinessException("至少需要选择两个方案进行对比");
        }
        if (new HashSet<>(planIds).size() != planIds.size()) {
            throw new BusinessException("不能选择重复方案进行对比");
        }

        List<ComparePlanVo> plans = new ArrayList<>();
        for (Long planId : planIds) {
            SchedulePlan plan = planMapper.selectById(planId);
            if (plan == null) {
                throw new BusinessException("方案不存在：" + planId);
            }
            if (!Objects.equals(plan.getSemesterId(), semesterId)) {
                throw new BusinessException("方案 " + plan.getName() + " 不属于当前学期");
            }
            plans.add(buildPlanCompareInfo(plan));
        }

        // 推荐方案：总分最高 → 未排最少 → 冲突最少
        Long bestPlanId = plans.stream()
                .max(Comparator
                        .comparingDouble((ComparePlanVo p) -> p.getTotalScore().doubleValue())
                        .thenComparingInt(p -> -nullSafeInt(p.getUnscheduledCount()))
                        .thenComparingInt(p -> -nullSafeInt(p.getConflictCount())))
                .map(ComparePlanVo::getPlanId)
                .orElse(null);

        String summary = buildSummary(plans, bestPlanId);

        return new CompareResultVo(semesterId, plans, bestPlanId, summary);
    }

    private ComparePlanVo buildPlanCompareInfo(SchedulePlan plan) {
        List<ScheduleScoreDetail> details = scoreService.getScoreDetails(plan.getId());
        int hardViolation = 0;
        int softViolation = 0;
        for (ScheduleScoreDetail d : details) {
            if (d.getScore() != null && d.getScore().compareTo(BigDecimal.ZERO) < 0) {
                if ("HARD".equals(d.getRuleType())) {
                    hardViolation++;
                } else {
                    softViolation++;
                }
            }
        }

        return new ComparePlanVo(
                plan.getId(),
                plan.getName(),
                plan.getStrategyType(),
                strategyName(plan.getStrategyType()),
                plan.getStatus(),
                plan.getTotalScore() != null ? plan.getTotalScore() : BigDecimal.ZERO,
                plan.getScheduledCount(),
                plan.getUnscheduledCount(),
                plan.getConflictCount(),
                hardViolation,
                softViolation,
                plan.getGeneratedAt());
    }

    private String strategyName(String type) {
        return switch (type) {
            case "TEACHER_PRIORITY" -> "教师优先";
            case "CLASS_BALANCE" -> "班级均衡";
            case "CLASSROOM_UTILIZATION" -> "教室利用率";
            case "COMPREHENSIVE" -> "综合最优";
            default -> type;
        };
    }

    private String buildSummary(List<ComparePlanVo> plans, Long bestPlanId) {
        if (bestPlanId == null) return "无法确定推荐方案";

        ComparePlanVo best = plans.stream()
                .filter(p -> Objects.equals(p.getPlanId(), bestPlanId))
                .findFirst()
                .orElse(null);
        if (best == null) return "无法确定推荐方案";

        String bestName = best.getPlanName();
        BigDecimal bestScore = best.getTotalScore();
        int unscheduled = nullSafeInt(best.getUnscheduledCount());
        int conflicts = nullSafeInt(best.getConflictCount());

        StringBuilder sb = new StringBuilder();
        sb.append(bestName).append(" 总分最高（").append(bestScore).append("分）");
        if (unscheduled > 0) {
            sb.append("，存在 ").append(unscheduled).append(" 个未排任务");
        } else {
            sb.append("，所有任务均已排入");
        }
        if (conflicts > 0) {
            sb.append("，存在 ").append(conflicts).append(" 个冲突");
        } else {
            sb.append("，无冲突");
        }
        sb.append("，推荐应用为正式课表。");
        return sb.toString();
    }

    /** 计数缺失时按 0 处理，沿用原 Map.getOrDefault(key, 0) 语义。 */
    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
