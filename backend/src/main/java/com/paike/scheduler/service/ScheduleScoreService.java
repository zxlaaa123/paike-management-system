package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleScoreDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleScoreService {

    private static final BigDecimal FULL_SCORE = new BigDecimal("100");

    private final ScheduleScoreDetailMapper scoreDetailMapper;
    private final SchedulePlanMapper planMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final ScheduleRuleWeightService ruleWeightService;
    private final ScheduleThresholdProperties thresholds;

    public List<ScheduleScoreDetail> getScoreDetails(Long planId) {
        return scoreDetailMapper.selectList(
                new LambdaQueryWrapper<ScheduleScoreDetail>()
                        .eq(ScheduleScoreDetail::getPlanId, planId)
                        .orderByAsc(ScheduleScoreDetail::getRuleCode));
    }

    public BigDecimal getScoreSummary(Long planId) {
        SchedulePlan plan = planMapper.selectById(planId);
        if (plan == null || plan.getTotalScore() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return normalizeTotalScore(plan.getTotalScore());
    }

    public List<ScheduleScoreDetail> previewScoreDetails(Long planId) {
        SchedulePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            return List.of();
        }
        List<ScheduleRuleWeight> rules = ruleWeightService.list(plan.getSemesterId(), plan.getStrategyType(), null);
        if (rules.isEmpty()) {
            return List.of();
        }
        List<SchedulePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>().eq(SchedulePlanItem::getPlanId, plan.getId()));
        ScoreContext context = buildScoreContext(plan, items);
        List<ScheduleScoreDetail> details = new ArrayList<>();
        for (ScheduleRuleWeight rule : rules) {
            if (rule.getEnabled() == null || rule.getEnabled() == 0) {
                continue;
            }
            details.add(buildDetail(plan, rule, context));
        }
        return details;
    }

    /**
     * Recomputes score details and persists the summary fields back to the given plan.
     */
    @Transactional(rollbackFor = Exception.class)
    public void rescore(SchedulePlan plan) {
        scoreDetailMapper.delete(
                new LambdaQueryWrapper<ScheduleScoreDetail>()
                        .eq(ScheduleScoreDetail::getPlanId, plan.getId()));

        List<ScheduleRuleWeight> rules = ruleWeightService.list(plan.getSemesterId(), plan.getStrategyType(), null);
        if (rules.isEmpty()) {
            ruleWeightService.initDefaultRules(plan.getSemesterId(), plan.getStrategyType());
            rules = ruleWeightService.list(plan.getSemesterId(), plan.getStrategyType(), null);
        }

        List<SchedulePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>().eq(SchedulePlanItem::getPlanId, plan.getId()));
        ScoreContext context = buildScoreContext(plan, items);

        List<ScheduleScoreDetail> details = new ArrayList<>();
        BigDecimal totalPenalty = BigDecimal.ZERO;
        for (ScheduleRuleWeight rule : rules) {
            if (rule.getEnabled() == null || rule.getEnabled() == 0) {
                continue;
            }
            ScheduleScoreDetail detail = buildDetail(plan, rule, context);
            details.add(detail);
            totalPenalty = totalPenalty.add(detail.getScore() != null ? detail.getScore() : BigDecimal.ZERO);
        }

        for (ScheduleScoreDetail detail : details) {
            scoreDetailMapper.insert(detail);
        }

        plan.setTotalScore(normalizeTotalScore(FULL_SCORE.add(totalPenalty)));
        plan.setConflictCount(context.teacherConflictCount()
                + context.classConflictCount()
                + context.roomConflictCount()
                + context.teacherUnavailableCount()
                + context.capacityViolationCount()
                + context.roomTypeMismatchCount());
        planMapper.updateById(plan);
    }

    private ScheduleScoreDetail buildDetail(SchedulePlan plan, ScheduleRuleWeight rule, ScoreContext context) {
        MetricResult metric = calculateMetric(rule, context);
        ScheduleScoreDetail detail = new ScheduleScoreDetail();
        detail.setPlanId(plan.getId());
        detail.setSemesterId(plan.getSemesterId());
        detail.setRuleCode(rule.getRuleCode());
        detail.setRuleType(rule.getRuleType());
        detail.setRuleName(rule.getRuleName());
        detail.setScore(metric.score());
        detail.setMaxScore(rule.getWeight());
        detail.setViolationCount(metric.violationCount());
        detail.setDetailMessage(metric.message());
        detail.setCreatedAt(LocalDateTime.now());
        return detail;
    }

    private MetricResult calculateMetric(ScheduleRuleWeight rule, ScoreContext context) {
        BigDecimal weight = safeWeight(rule.getWeight());
        String ruleCode = rule.getRuleCode();

        return switch (ruleCode) {
            case "TEACHER_TIME_CONFLICT" -> buildHardMetric(rule, weight, context.teacherConflictCount(), "教师时间冲突");
            case "CLASS_TIME_CONFLICT" -> buildHardMetric(rule, weight, context.classConflictCount(), "班级时间冲突");
            case "CLASSROOM_TIME_CONFLICT" -> buildHardMetric(rule, weight, context.roomConflictCount(), "教室时间冲突");
            case "TEACHER_UNAVAILABLE" -> buildHardMetric(rule, weight, context.teacherUnavailableCount(), "教师禁排时间");
            case "CLASSROOM_CAPACITY" -> buildHardMetric(rule, weight, context.capacityViolationCount(), "教室容量不足");
            case "CLASSROOM_TYPE_MISMATCH" -> buildHardMetric(rule, weight, context.roomTypeMismatchCount(), "教室类型不匹配");
            case "CLASS_DAILY_BALANCE" -> buildSoftMetric(weight, context.classBalancePenalty(), "班级每日均衡");
            case "TEACHER_DAILY_LOAD" -> buildSoftMetric(weight, context.teacherLoadPenalty(), "教师每日负载");
            case "CONTINUOUS_PERIOD_LIMIT" -> buildSoftMetric(weight, context.continuousPenalty(), "连续上课限制");
            case "COURSE_DISTRIBUTION" -> buildSoftMetric(weight, context.courseDistributionPenalty(), "课程分布均衡");
            case "CLASSROOM_UTILIZATION" -> buildSoftMetric(weight, context.classroomUtilizationPenalty(), "教室利用率");
            case "MORNING_THEORY_PRIORITY" -> buildSoftMetric(weight, context.morningPriorityPenalty(), "理论课优先上午");
            default -> new MetricResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, "无评分逻辑，按 0 处理");
        };
    }

    private MetricResult buildHardMetric(ScheduleRuleWeight rule, BigDecimal weight, int violationCount, String label) {
        if (violationCount <= 0) {
            return new MetricResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, "无违规");
        }
        BigDecimal score = weight.multiply(new BigDecimal(violationCount)).negate().setScale(2, RoundingMode.HALF_UP);
        return new MetricResult(score, violationCount, label + "违规 " + violationCount + " 次，扣 " + score.abs() + " 分");
    }

    private MetricResult buildSoftMetric(BigDecimal weight, BigDecimal penaltyFactor, String label) {
        BigDecimal normalized = penaltyFactor == null ? BigDecimal.ZERO : penaltyFactor.max(BigDecimal.ZERO);
        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            return new MetricResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, "表现良好");
        }
        BigDecimal clamped = normalized.min(BigDecimal.ONE);
        BigDecimal score = weight.multiply(clamped).negate().setScale(2, RoundingMode.HALF_UP);
        int level = clamped.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue();
        return new MetricResult(score, level, label + "偏差 " + level + "%，扣 " + score.abs() + " 分");
    }

    private ScoreContext buildScoreContext(SchedulePlan plan, List<SchedulePlanItem> items) {
        Map<String, List<SchedulePlanItem>> teacherSlotMap = groupBy(items, item -> item.getTeacherId() + "_" + item.getWeekday() + "_" + item.getStartPeriod());
        Map<String, List<SchedulePlanItem>> classSlotMap = groupBy(items, item -> item.getClassId() + "_" + item.getWeekday() + "_" + item.getStartPeriod());
        Map<String, List<SchedulePlanItem>> roomSlotMap = groupBy(items, item -> item.getClassroomId() + "_" + item.getWeekday() + "_" + item.getStartPeriod());

        Map<Long, Map<Integer, Long>> classDayCounts = nestedDayCounts(items, SchedulePlanItem::getClassId);
        Map<Long, Map<Integer, Long>> teacherDayCounts = nestedDayCounts(items, SchedulePlanItem::getTeacherId);
        Map<String, Long> courseDayCounts = items.stream().collect(Collectors.groupingBy(
                item -> item.getClassId() + "_" + item.getCourseId() + "_" + item.getWeekday(),
                Collectors.counting()
        ));
        Map<Long, Map<Integer, List<SchedulePlanItem>>> teacherDayItems = nestedDayItems(items, SchedulePlanItem::getTeacherId);
        Map<Long, Long> roomUseCounts = items.stream().collect(Collectors.groupingBy(SchedulePlanItem::getClassroomId, Collectors.counting()));

        int teacherConflictCount = countConflicts(teacherSlotMap);
        int classConflictCount = countConflicts(classSlotMap);
        int roomConflictCount = countConflicts(roomSlotMap);
        int teacherUnavailableCount = countConflictReason(items, PlanConflictType.TEACHER_UNAVAILABLE);
        int capacityViolationCount = countConflictReason(items, PlanConflictType.CLASSROOM_CAPACITY);
        int roomTypeMismatchCount = countConflictReason(items, PlanConflictType.CLASSROOM_TYPE_MISMATCH);

        BigDecimal classBalancePenalty = variancePenalty(classDayCounts);
        BigDecimal teacherLoadPenalty = variancePenalty(teacherDayCounts);
        BigDecimal courseDistributionPenalty = duplicateCoursePenalty(courseDayCounts);
        BigDecimal continuousPenalty = continuousPenalty(teacherDayItems);
        BigDecimal classroomUtilizationPenalty = classroomUtilizationPenalty(roomUseCounts, items.size());
        BigDecimal morningPriorityPenalty = morningPriorityPenalty(items);

        return new ScoreContext(
                teacherConflictCount,
                classConflictCount,
                roomConflictCount,
                teacherUnavailableCount,
                capacityViolationCount,
                roomTypeMismatchCount,
                classBalancePenalty,
                teacherLoadPenalty,
                continuousPenalty,
                courseDistributionPenalty,
                classroomUtilizationPenalty,
                morningPriorityPenalty,
                plan.getUnscheduledCount() == null ? 0 : plan.getUnscheduledCount()
        );
    }

    private <K> Map<String, List<SchedulePlanItem>> groupBy(List<SchedulePlanItem> items, Function<SchedulePlanItem, String> keyFunc) {
        return items.stream().collect(Collectors.groupingBy(keyFunc));
    }

    private Map<Long, Map<Integer, Long>> nestedDayCounts(List<SchedulePlanItem> items, Function<SchedulePlanItem, Long> ownerFunc) {
        return items.stream().collect(Collectors.groupingBy(
                ownerFunc,
                Collectors.groupingBy(SchedulePlanItem::getWeekday, Collectors.counting())
        ));
    }

    private Map<Long, Map<Integer, List<SchedulePlanItem>>> nestedDayItems(List<SchedulePlanItem> items, Function<SchedulePlanItem, Long> ownerFunc) {
        return items.stream().collect(Collectors.groupingBy(
                ownerFunc,
                Collectors.groupingBy(SchedulePlanItem::getWeekday)
        ));
    }

    private int countConflicts(Map<String, List<SchedulePlanItem>> grouped) {
        return grouped.values().stream()
                .mapToInt(list -> Math.max(0, list.size() - 1))
                .sum();
    }

    private BigDecimal variancePenalty(Map<Long, Map<Integer, Long>> countsByOwner) {
        if (countsByOwner.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double penalty = 0D;
        for (Map<Integer, Long> dayCounts : countsByOwner.values()) {
            if (dayCounts.size() <= 1) {
                continue;
            }
            double avg = dayCounts.values().stream().mapToLong(Long::longValue).average().orElse(0D);
            double variance = dayCounts.values().stream()
                    .mapToDouble(count -> Math.pow(count - avg, 2))
                    .average()
                    .orElse(0D);
            penalty += Math.min(1D, variance / 4D);
        }
        double normalized = penalty / countsByOwner.size();
        return BigDecimal.valueOf(normalized).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal duplicateCoursePenalty(Map<String, Long> courseDayCounts) {
        if (courseDayCounts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long duplicateDays = courseDayCounts.values().stream().filter(count -> count > 1).count();
        long totalDays = courseDayCounts.size();
        if (totalDays == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((double) duplicateDays / totalDays).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal continuousPenalty(Map<Long, Map<Integer, List<SchedulePlanItem>>> teacherDayItems) {
        if (teacherDayItems.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double penalty = 0D;
        int sampleCount = 0;
        for (Map<Integer, List<SchedulePlanItem>> dayItems : teacherDayItems.values()) {
            for (List<SchedulePlanItem> items : dayItems.values()) {
                sampleCount++;
                List<Integer> starts = items.stream()
                        .map(SchedulePlanItem::getStartPeriod)
                        .sorted()
                        .toList();
                int consecutiveChains = 0;
                for (int i = 1; i < starts.size(); i++) {
                    if (starts.get(i) - starts.get(i - 1) == 2) {
                        consecutiveChains++;
                    }
                }
                penalty += Math.min(1D, consecutiveChains / 2D);
            }
        }
        if (sampleCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(penalty / sampleCount).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal classroomUtilizationPenalty(Map<Long, Long> roomUseCounts, int totalItems) {
        if (totalItems <= 0 || roomUseCounts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double avg = (double) totalItems / roomUseCounts.size();
        double variance = roomUseCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - avg, 2))
                .average()
                .orElse(0D);
        return BigDecimal.valueOf(Math.min(1D, variance / Math.max(1D, avg * avg))).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal morningPriorityPenalty(List<SchedulePlanItem> items) {
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long afternoonCount = items.stream().filter(item -> item.getStartPeriod() >= thresholds.getAfternoonStartPeriod()).count();
        return BigDecimal.valueOf((double) afternoonCount / items.size()).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal safeWeight(BigDecimal weight) {
        return weight != null ? weight : BigDecimal.ZERO;
    }

    private BigDecimal normalizeTotalScore(BigDecimal totalScore) {
        if (totalScore == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (totalScore.compareTo(BigDecimal.ZERO) < 0) {
            totalScore = BigDecimal.ZERO;
        }
        if (totalScore.compareTo(FULL_SCORE) > 0) {
            totalScore = FULL_SCORE;
        }
        return totalScore.setScale(2, RoundingMode.HALF_UP);
    }

    private int countConflictReason(List<SchedulePlanItem> items, PlanConflictType targetType) {
        return (int) items.stream()
                .filter(item -> hasConflictReason(item, targetType))
                .count();
    }

    private boolean hasConflictReason(SchedulePlanItem item, PlanConflictType targetType) {
        if (item.getConflictReason() == null || item.getConflictReason().isBlank()) {
            return false;
        }
        return Arrays.stream(item.getConflictReason().split("；"))
                .map(String::trim)
                .map(this::normalizeConflictReason)
                .anyMatch(targetType::matches);
    }

    private String normalizeConflictReason(String reason) {
        int detailIndex = reason.indexOf('：');
        return detailIndex >= 0 ? reason.substring(0, detailIndex) : reason;
    }

    private enum PlanConflictType {
        TEACHER_UNAVAILABLE("教师禁排时间冲突"),
        CLASSROOM_CAPACITY("教室容量不足"),
        CLASSROOM_TYPE_MISMATCH("教室类型不匹配");

        private final String legacyLabel;

        PlanConflictType(String legacyLabel) {
            this.legacyLabel = legacyLabel;
        }

        private boolean matches(String reason) {
            return name().equals(reason) || legacyLabel.equals(reason);
        }
    }

    private record MetricResult(BigDecimal score, int violationCount, String message) {
    }

    private record ScoreContext(
            int teacherConflictCount,
            int classConflictCount,
            int roomConflictCount,
            int teacherUnavailableCount,
            int capacityViolationCount,
            int roomTypeMismatchCount,
            BigDecimal classBalancePenalty,
            BigDecimal teacherLoadPenalty,
            BigDecimal continuousPenalty,
            BigDecimal courseDistributionPenalty,
            BigDecimal classroomUtilizationPenalty,
            BigDecimal morningPriorityPenalty,
            int unscheduledCount
    ) {
    }
}
