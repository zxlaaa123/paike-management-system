package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleScoreDetailMapper;
import com.paike.scheduler.service.scheduling.ScoringFunctions;
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
    private final ClassroomMapper classroomMapper;
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

    /**
     * 事后<b>离线评分</b>：方案生成完调一次，给每条规则算扣分写库。
     * 注意跟 V3ScheduleGenerateService.scoreCandidate（在线、贪心选候选）是<b>双轨</b>关系，
     * 同名规则码（如 CLASSROOM_UTILIZATION）两边公式不同 —— 参见
     * {@link com.paike.scheduler.service.scheduling.ScoringDimensions#ONLINE_SOFT}
     * 和 {@link com.paike.scheduler.service.scheduling.ScoringDimensions#OFFLINE_SOFT}。
     * 总分公式：clamp(100 + Σ rule.score, 0, 100)，软规则 score = -weight×min(1,penalty)，
     * 硬规则 score = -weight×violationCount。
     */
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
        String maxScore = formatWeight(weight);
        if (violationCount <= 0) {
            return new MetricResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0,
                    label + "无违规（满分 " + maxScore + "）");
        }
        BigDecimal score = weight.multiply(new BigDecimal(violationCount)).negate().setScale(2, RoundingMode.HALF_UP);
        return new MetricResult(score, violationCount,
                label + "违规 " + violationCount + " 次，扣 " + score.abs() + " 分（满分 " + maxScore + "）");
    }

    private MetricResult buildSoftMetric(BigDecimal weight, BigDecimal penaltyFactor, String label) {
        String maxScore = formatWeight(weight);
        BigDecimal normalized = penaltyFactor == null ? BigDecimal.ZERO : penaltyFactor.max(BigDecimal.ZERO);
        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            return new MetricResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0,
                    label + "表现良好（满分 " + maxScore + "）");
        }
        BigDecimal clamped = normalized.min(BigDecimal.ONE);
        BigDecimal score = weight.multiply(clamped).negate().setScale(2, RoundingMode.HALF_UP);
        int level = clamped.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue();
        return new MetricResult(score, level,
                label + "偏差 " + level + "%，扣 " + score.abs() + " 分（满分 " + maxScore + "）");
    }

    private String formatWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return weight.stripTrailingZeros().toPlainString();
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
        Map<Long, Long> roomUseCounts = activeClassroomUseCounts();
        items.stream()
                .map(SchedulePlanItem::getClassroomId)
                .filter(Objects::nonNull)
                .forEach(roomId -> roomUseCounts.merge(roomId, 1L, ScheduleScoreService::sumLongs));

        int teacherConflictCount = countConflicts(teacherSlotMap);
        int classConflictCount = countConflicts(classSlotMap);
        int roomConflictCount = countConflicts(roomSlotMap);
        int teacherUnavailableCount = countConflictReason(items, PlanConflictType.TEACHER_UNAVAILABLE);
        int capacityViolationCount = countConflictReason(items, PlanConflictType.CLASSROOM_CAPACITY);
        int roomTypeMismatchCount = countConflictReason(items, PlanConflictType.CLASSROOM_TYPE_MISMATCH);

        BigDecimal classBalancePenalty = ScoringFunctions.penaltyVariance(classDayCounts);
        BigDecimal teacherLoadPenalty = ScoringFunctions.penaltyVariance(teacherDayCounts);
        BigDecimal courseDistributionPenalty = ScoringFunctions.penaltyDuplicateCourse(courseDayCounts);
        BigDecimal continuousPenalty = ScoringFunctions.penaltyContinuous(teacherDayItems);
        BigDecimal classroomUtilizationPenalty = ScoringFunctions.penaltyClassroomUtilization(roomUseCounts, items.size());
        BigDecimal morningPriorityPenalty = ScoringFunctions.penaltyMorningPriority(items, thresholds.getAfternoonStartPeriod());

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

    private Map<Long, Long> activeClassroomUseCounts() {
        List<Classroom> classrooms = classroomMapper.selectList(
                new LambdaQueryWrapper<Classroom>()
                        .eq(Classroom::getStatus, 1)
                        .eq(Classroom::getDeleted, 0));
        if (classrooms == null || classrooms.isEmpty()) {
            return new HashMap<>();
        }
        return classrooms.stream()
                .map(Classroom::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), id -> 0L));
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

    private static Long sumLongs(Long left, Long right) {
        long safeLeft = left == null ? 0L : left;
        long safeRight = right == null ? 0L : right;
        return safeLeft + safeRight;
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
