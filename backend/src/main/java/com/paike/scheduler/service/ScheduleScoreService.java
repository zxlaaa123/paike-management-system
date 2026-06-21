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
import com.paike.scheduler.service.scheduling.ScoringFunctions.WeekOwner;
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
            case "CLASS_GAP_PENALTY" -> buildSoftMetric(weight, context.classGapPenalty(), "班级空堂惩罚");
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
        // V9 阶段 2A β 评分：硬冲突计数用 (owner,day,period) 同槽分组后做 weekType overlap 成对判定，
        // ODD+EVEN 共槽（合法）不计冲突；纯 ALL 数据组内全 overlap，结果仍为 size-1（零回归）。
        Map<String, List<SchedulePlanItem>> teacherSlotMap = groupBy(items, item -> item.getTeacherId() + "_" + item.getWeekday() + "_" + item.getStartPeriod());
        Map<String, List<SchedulePlanItem>> classSlotMap = groupBy(items, item -> item.getClassId() + "_" + item.getWeekday() + "_" + item.getStartPeriod());
        Map<String, List<SchedulePlanItem>> roomSlotMap = groupBy(items, item -> item.getClassroomId() + "_" + item.getWeekday() + "_" + item.getStartPeriod());

        // β 评分（独立计数）：owner 维度加 (weekType, weekMask)，ALL 展开成 ODD+EVEN 两个独立子桶，
        // 再按实际自然周 mask 区分（V10：周段不相交 → 不同桶 → 互不影响）。
        Map<WeekOwner, Map<Integer, Long>> classDayCounts = nestedDayCountsBeta(items, SchedulePlanItem::getClassId);
        Map<WeekOwner, Map<Integer, Long>> teacherDayCounts = nestedDayCountsBeta(items, SchedulePlanItem::getTeacherId);
        Map<String, Long> courseDayCounts = items.stream()
                .flatMap(item -> WeekTypeSupport.countableWeekTypes(item.getWeekType()).stream()
                        .map(wt -> new AbstractMap.SimpleEntry<>(
                                item.getClassId() + "_" + item.getCourseId() + "_" + item.getWeekday() + "_" + wt
                                        + "_" + WeekPatternSupport.weekRangeKey(item.getWeekType(), item.getStartWeek(), item.getEndWeek()),
                                1L)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.summingLong(Map.Entry::getValue)));
        Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> teacherDayItems = nestedDayItemsBeta(items, SchedulePlanItem::getTeacherId);
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

        BigDecimal classBalancePenalty = ScoringFunctions.penaltyVarianceBeta(classDayCounts);
        BigDecimal teacherLoadPenalty = ScoringFunctions.penaltyVarianceBeta(teacherDayCounts);
        BigDecimal courseDistributionPenalty = ScoringFunctions.penaltyDuplicateCourse(courseDayCounts);
        BigDecimal continuousPenalty = ScoringFunctions.penaltyContinuousBeta(teacherDayItems);
        BigDecimal classGapPenalty = ScoringFunctions.penaltyClassGapBeta(nestedDayItemsBeta(items, SchedulePlanItem::getClassId));
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
                classGapPenalty,
                plan.getUnscheduledCount() == null ? 0 : plan.getUnscheduledCount()
        );
    }

    private <K> Map<String, List<SchedulePlanItem>> groupBy(List<SchedulePlanItem> items, Function<SchedulePlanItem, String> keyFunc) {
        return items.stream().collect(Collectors.groupingBy(keyFunc));
    }

    private Map<Long, Long> activeClassroomUseCounts() {
        List<Classroom> classrooms = classroomMapper.selectList(
                new LambdaQueryWrapper<Classroom>()
                        .eq(Classroom::getStatus, 1));
        if (classrooms == null || classrooms.isEmpty()) {
            return new HashMap<>();
        }
        return classrooms.stream()
                .map(Classroom::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), id -> 0L));
    }

    /**
     * V10 β 版：owner 维度加 (weekType, weekMask)，ALL 用
     * {@link WeekTypeSupport#countableWeekTypes(String)} 展开成 ODD+EVEN 两个独立子桶，
     * 再按 {@link WeekPatternSupport#weekMask} 区分实际自然周集合。
     * 下游用 {@link ScoringFunctions#penaltyVarianceBeta(Map)}。
     */
    private Map<WeekOwner, Map<Integer, Long>> nestedDayCountsBeta(List<SchedulePlanItem> items, Function<SchedulePlanItem, Long> ownerFunc) {
        return items.stream()
                .flatMap(item -> WeekTypeSupport.countableWeekTypes(item.getWeekType()).stream()
                        .map(wt -> new AbstractMap.SimpleEntry<>(
                                weekOwner(ownerFunc.apply(item), wt, item), item.getWeekday())))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.groupingBy(
                                Map.Entry::getValue,
                                Collectors.counting())));
    }

    /**
     * V10 β 版：同 {@link #nestedDayCountsBeta}，连续上课限制按 (teacher × weekType × weekMask × day) 分桶。
     */
    private Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> nestedDayItemsBeta(List<SchedulePlanItem> items, Function<SchedulePlanItem, Long> ownerFunc) {
        Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> result = new HashMap<>();
        for (SchedulePlanItem item : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(item.getWeekType())) {
                WeekOwner key = weekOwner(ownerFunc.apply(item), wt, item);
                result.computeIfAbsent(key, k -> new HashMap<>())
                        .computeIfAbsent(item.getWeekday(), d -> new ArrayList<>())
                        .add(item);
            }
        }
        return result;
    }

    /** V10：构造带 weekMask 的 WeekOwner */
    private WeekOwner weekOwner(Long ownerId, String weekType, SchedulePlanItem item) {
        return new WeekOwner(ownerId, weekType,
                WeekPatternSupport.weekRangeKey(item.getWeekType(), item.getStartWeek(), item.getEndWeek()));
    }

    /**
     * V10：硬冲突计数按 {@link WeekPatternSupport#overlap} 判定。
     * 同 (owner,day,period) 分组后，组内两两判定实际自然周集合是否相交。
     * 纯 ALL 1-20 数据组内全 overlap，结果仍为 size-1（零回归）；
     * ODD+EVEN 共槽（合法）不冲突；周段不相交（如 ALL 1-8 vs ALL 9-16）不冲突。
     *
     * <p>同组内 N 条记录中真正冲突数 = 必须移除才能两两 overlap=false 的最小数。
     * 贪心实现：按 mask 降序（周数多的优先保留），逐条检查与已保留的是否 overlap，
     * overlap 则计为冲突，否则保留。
     */
    private int countConflicts(Map<String, List<SchedulePlanItem>> grouped) {
        int total = 0;
        for (List<SchedulePlanItem> list : grouped.values()) {
            if (list.size() <= 1) {
                continue;
            }
            total += countOverlappingConflicts(list);
        }
        return total;
    }

    /**
     * V10：组内按 mask 降序贪心，与已保留的任意一条 overlap 即为冲突。
     */
    private int countOverlappingConflicts(List<SchedulePlanItem> list) {
        int conflicts = 0;
        List<SchedulePlanItem> retained = new ArrayList<>();
        for (SchedulePlanItem item : list) {
            boolean overlap = false;
            for (SchedulePlanItem kept : retained) {
                if (WeekPatternSupport.overlap(
                        item.getWeekType(), item.getStartWeek(), item.getEndWeek(),
                        kept.getWeekType(), kept.getStartWeek(), kept.getEndWeek())) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) {
                conflicts++;
            } else {
                retained.add(item);
            }
        }
        return conflicts;
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
            BigDecimal classGapPenalty,
            int unscheduledCount
    ) {
    }
}
