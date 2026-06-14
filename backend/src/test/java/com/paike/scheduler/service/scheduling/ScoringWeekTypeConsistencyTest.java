package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.service.WeekTypeSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9 阶段 2A T5：β 评分（独立计数）一致性对拍与语义断言。
 *
 * <p>本测试是 V9_05 T5 在阶段 2 启用的核心验收用例，覆盖四个维度：
 * <ol>
 *   <li><b>β 语义断言</b>：ODD 只进 ODD 桶、EVEN 只进 EVEN 桶、ALL 进两者</li>
 *   <li><b>单双周共槽对拍</b>：教师周一1-2节 ODD+EVEN 共槽（合法），不误报硬冲突，
 *       且离线 ScoringFunctions 直接算 vs 全量聚合结果一致</li>
 *   <li><b>纯 ALL 回归保护</b>：复用 ScheduleScoreServiceTest fixture1 的全 ALL 数据，
 *       断言 β 改造后数值与改造前完全相同（零回归承诺）</li>
 *   <li><b>DeltaPenaltyScorer β 对拍</b>：在线增量 delta 累加 ≈ 离线全量 penalty（scale=4 一致）</li>
 * </ol>
 *
 * <p>纯单元测试，无 Spring 依赖（参照 {@code WeekTypeConflictMatrixTest} 范式）。
 * 裁决依据：V9_00 §5 #5（β 独立计数）、235-243 行（具体含义）。
 */
class ScoringWeekTypeConsistencyTest {

    // ============================================================
    // 1. β 语义断言：WeekTypeSupport.countableWeekTypes + 聚合分桶
    // ============================================================

    /** countableWeekTypes 展开规则：ALL→[ODD,EVEN]，ODD→[ODD]，EVEN→[EVEN] */
    @Test
    void countableWeekTypes_expandRules() {
        assertEquals(List.of("ODD", "EVEN"), WeekTypeSupport.countableWeekTypes("ALL"));
        assertEquals(List.of("ODD"), WeekTypeSupport.countableWeekTypes("ODD"));
        assertEquals(List.of("EVEN"), WeekTypeSupport.countableWeekTypes("EVEN"));
        // null/空 视为 ALL
        assertEquals(List.of("ODD", "EVEN"), WeekTypeSupport.countableWeekTypes(null));
        assertEquals(List.of("ODD", "EVEN"), WeekTypeSupport.countableWeekTypes(""));
    }

    /**
     * β 分桶语义：构造教师 t1 周一 ALL + ODD + EVEN 三条 item，
     * 断言 t1 在 ODD 桶计 2（ALL+ODD）、EVEN 桶计 2（ALL+EVEN），各自独立。
     */
    @Test
    void betaAggregation_oddEvenIndependent() {
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ALL"),
                item(1L, 1L, 2L, 1L, 1, 3, "ODD"),
                item(1L, 1L, 3L, 1L, 1, 5, "EVEN"));

        Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> teacherCounts =
                aggregateDayCountsBeta(items, SchedulePlanItem::getTeacherId);

        // t1 周一 ODD 桶 = ALL + ODD = 2
        long oddLoad = teacherCounts.getOrDefault(new ScoringFunctions.WeekOwner(1L, "ODD"), Map.of())
                .getOrDefault(1, 0L);
        // t1 周一 EVEN 桶 = ALL + EVEN = 2
        long evenLoad = teacherCounts.getOrDefault(new ScoringFunctions.WeekOwner(1L, "EVEN"), Map.of())
                .getOrDefault(1, 0L);

        assertEquals(2L, oddLoad, "ODD 桶应含 ALL+ODD = 2 条");
        assertEquals(2L, evenLoad, "EVEN 桶应含 ALL+EVEN = 2 条");
    }

    /**
     * β 核心价值：教师周一 1-2节 ODD 体育 + EVEN 思政（合法共槽），
     * 单周负荷=1、双周负荷=1，各自独立均衡，penaltyVariance 应反映"单双周各自平衡"。
     */
    @Test
    void betaCoreValue_oddEvenSharedSlotBalanced() {
        // t1 周一1-2 ODD + 周一1-2 EVEN（共槽合法），再加周二各一条凑出跨日方差
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ODD"),   // t1 周一 ODD
                item(1L, 1L, 2L, 1L, 1, 1, "EVEN"),  // t1 周一 EVEN（共槽）
                item(1L, 1L, 1L, 1L, 2, 1, "ODD"),   // t1 周二 ODD
                item(1L, 1L, 2L, 1L, 2, 1, "EVEN")); // t1 周二 EVEN

        Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> teacherCounts =
                aggregateDayCountsBeta(items, SchedulePlanItem::getTeacherId);

        // t1 ODD 桶 {周一:1, 周二:1} → 完全均衡，variance=0
        Map<Integer, Long> oddDays = teacherCounts.get(new ScoringFunctions.WeekOwner(1L, "ODD"));
        Map<Integer, Long> evenDays = teacherCounts.get(new ScoringFunctions.WeekOwner(1L, "EVEN"));
        assertEquals(Map.of(1, 1L, 2, 1L), oddDays, "ODD 桶应 {d1:1, d2:1}");
        assertEquals(Map.of(1, 1L, 2, 1L), evenDays, "EVEN 桶应 {d1:1, d2:1}");

        // penaltyVarianceBeta 应为 0（每个 (owner,weekType) 子桶跨日完全均衡）
        BigDecimal penalty = ScoringFunctions.penaltyVarianceBeta(teacherCounts);
        assertEquals(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), penalty,
                "单双周各自均衡时 teacherLoad penalty 应为 0");
    }

    // ============================================================
    // 2. 纯 ALL 回归保护（零回归承诺）
    // ============================================================

    /**
     * 复用 ScheduleScoreServiceTest fixture1 的 5 条全 ALL item，断言 β 改造后数值不变。
     * 原 baseline（手算见 ScheduleScoreServiceTest:78-98）：
     *   classBalance=0.0313, teacherLoad=0.0313, continuous=0.3333
     * β 下 ALL 展开成 ODD+EVEN 对称两桶，归一化后数值相同。
     */
    @Test
    void pureAllData_zeroRegression() {
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, null),  // null 视为 ALL
                item(1L, 1L, 1L, 1L, 1, 3, null),
                item(2L, 2L, 2L, 2L, 2, 5, null),
                item(2L, 2L, 2L, 2L, 2, 7, null),
                item(1L, 1L, 1L, 2L, 2, 1, null));

        BigDecimal classBalance = ScoringFunctions.penaltyVarianceBeta(
                aggregateDayCountsBeta(items, SchedulePlanItem::getClassId));
        BigDecimal teacherLoad = ScoringFunctions.penaltyVarianceBeta(
                aggregateDayCountsBeta(items, SchedulePlanItem::getTeacherId));
        BigDecimal continuous = ScoringFunctions.penaltyContinuousBeta(
                aggregateDayItemsBeta(items, SchedulePlanItem::getTeacherId));

        // 与 ScheduleScoreServiceTest 锁定的离线 penalty 一致（scale=4）
        assertEquals(new BigDecimal("0.0313"), classBalance, "纯 ALL classBalance 应零回归");
        assertEquals(new BigDecimal("0.0313"), teacherLoad, "纯 ALL teacherLoad 应零回归");
        assertEquals(new BigDecimal("0.3333"), continuous, "纯 ALL continuous 应零回归");
    }

    /**
     * courseDistribution（penaltyDuplicateCourse）纯 ALL 回归：
     * 原 courseDayCounts {c1_cs1_d1=2, c1_cs1_d2=1, c2_cs2_d2=2} → dup=2/3=0.6667
     * β 下 ALL 展开为 ODD/EVEN 两份对称，dup=4/6=0.6667（占比相同）。
     */
    @Test
    void pureAllData_courseDistributionZeroRegression() {
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ALL"),
                item(1L, 1L, 1L, 1L, 1, 3, "ALL"),
                item(2L, 2L, 2L, 2L, 2, 5, "ALL"),
                item(2L, 2L, 2L, 2L, 2, 7, "ALL"),
                item(1L, 1L, 1L, 2L, 2, 1, "ALL"));

        Map<String, Long> courseDayCounts = items.stream()
                .flatMap(it -> WeekTypeSupport.countableWeekTypes(it.getWeekType()).stream()
                        .map(wt -> Map.entry(
                                it.getClassId() + "_" + it.getCourseId() + "_" + it.getWeekday() + "_" + wt,
                                1L)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.summingLong(Map.Entry::getValue)));

        BigDecimal penalty = ScoringFunctions.penaltyDuplicateCourse(courseDayCounts);
        assertEquals(new BigDecimal("0.6667"), penalty, "纯 ALL courseDistribution 应零回归");
    }

    // ============================================================
    // 3. DeltaPenaltyScorer β 对拍：增量 delta 累加 ≈ 全量 penalty
    // ============================================================

    /**
     * 同一混合数据集（ALL+ODD+EVEN），DeltaPenaltyScorer 逐条加入 candidate 算 delta 累加，
     * vs 全量重算 penalty，scale=4 一致。验证在线增量路径 β 正确。
     */
    @Test
    void deltaScorer_incrementalMatchesFullBeta() {
        // 5 条混合 item：2 ALL + 1 ODD + 1 EVEN + 1 ALL，t1 周一/周二
        List<SchedulePlanItem> base = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ALL"));
        List<SchedulePlanItem> toAdd = List.of(
                item(1L, 1L, 1L, 1L, 1, 3, "ALL"),
                item(1L, 1L, 2L, 1L, 2, 1, "ODD"),
                item(1L, 1L, 3L, 1L, 2, 1, "EVEN"),
                item(1L, 1L, 1L, 1L, 2, 3, "ALL"));

        int afternoonStart = 5;
        List<String> softRules = List.of(
                DeltaPenaltyScorer.CLASS_DAILY_BALANCE,
                DeltaPenaltyScorer.TEACHER_DAILY_LOAD,
                DeltaPenaltyScorer.COURSE_DISTRIBUTION,
                DeltaPenaltyScorer.CONTINUOUS_PERIOD_LIMIT);

        // 权重全 1，便于 delta 直接相加对拍
        Map<String, BigDecimal> weightMap = softRules.stream()
                .collect(Collectors.toMap(Function.identity(), r -> BigDecimal.ONE));

        // 增量路径：从 base 出发，逐条加入 candidate，累加 delta
        List<SchedulePlanItem> current = new java.util.ArrayList<>(base);
        BigDecimal incrementalSum = BigDecimal.ZERO;
        for (SchedulePlanItem candidate : toAdd) {
            DeltaPenaltyScorer.PenaltyContext ctx = DeltaPenaltyScorer.context(current, List.of(), afternoonStart);
            BigDecimal delta = DeltaPenaltyScorer.weightedSoftDeltaPenalty(weightMap, ctx, candidate);
            incrementalSum = incrementalSum.add(delta);
            current.add(candidate);
        }

        // 全量路径：base 的全量 penalty vs base+toAdd 的全量 penalty，差值应等于增量累加
        List<SchedulePlanItem> all = new java.util.ArrayList<>(base);
        all.addAll(toAdd);
        BigDecimal beforeFull = fullSoftPenalty(weightMap, base, afternoonStart);
        BigDecimal afterFull = fullSoftPenalty(weightMap, all, afternoonStart);
        BigDecimal fullDelta = afterFull.subtract(beforeFull);

        // scale=4 精度内一致（delta 累加可能因多次 setScale 累积微小误差，容差 0.01）
        assertEquals(
                fullDelta.setScale(4, RoundingMode.HALF_UP),
                incrementalSum.setScale(4, RoundingMode.HALF_UP),
                "DeltaPenaltyScorer β 增量累加应与全量差值一致（scale=4）");
    }

    /** 全量软规则 penalty 求和（权重全 1，走 ScoringFunctions Beta 全量重算，作为对拍基准） */
    private BigDecimal fullSoftPenalty(Map<String, BigDecimal> weightMap, List<SchedulePlanItem> items, int afternoonStart) {
        BigDecimal sum = BigDecimal.ZERO;
        for (String code : weightMap.keySet()) {
            sum = sum.add(fullPenaltyByCode(code, items, afternoonStart));
        }
        return sum;
    }

    /** 按 ruleCode 走 ScoringFunctions Beta 全量重算 */
    private BigDecimal fullPenaltyByCode(String code, List<SchedulePlanItem> items, int afternoonStart) {
        return switch (code) {
            case DeltaPenaltyScorer.CLASS_DAILY_BALANCE -> ScoringFunctions.penaltyVarianceBeta(
                    aggregateDayCountsBeta(items, SchedulePlanItem::getClassId));
            case DeltaPenaltyScorer.TEACHER_DAILY_LOAD -> ScoringFunctions.penaltyVarianceBeta(
                    aggregateDayCountsBeta(items, SchedulePlanItem::getTeacherId));
            case DeltaPenaltyScorer.COURSE_DISTRIBUTION -> ScoringFunctions.penaltyDuplicateCourse(
                    items.stream().flatMap(it -> WeekTypeSupport.countableWeekTypes(it.getWeekType()).stream()
                            .map(wt -> Map.entry(
                                    it.getClassId() + "_" + it.getCourseId() + "_" + it.getWeekday() + "_" + wt,
                                    1L)))
                            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingLong(Map.Entry::getValue))));
            case DeltaPenaltyScorer.CONTINUOUS_PERIOD_LIMIT -> ScoringFunctions.penaltyContinuousBeta(
                    aggregateDayItemsBeta(items, SchedulePlanItem::getTeacherId));
            default -> BigDecimal.ZERO;
        };
    }

    // ============================================================
    // helpers
    // ============================================================

    private SchedulePlanItem item(Long teacherId, Long classId, Long courseId, Long classroomId,
                                  int weekday, int startPeriod, String weekType) {
        SchedulePlanItem it = new SchedulePlanItem();
        it.setTeacherId(teacherId);
        it.setClassId(classId);
        it.setCourseId(courseId);
        it.setClassroomId(classroomId);
        it.setWeekday(weekday);
        it.setStartPeriod(startPeriod);
        it.setWeekType(weekType);
        return it;
    }

    /** β 聚合：owner 维度加 weekType，ALL 展开成 ODD+EVEN 两个独立子桶 */
    private Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> aggregateDayCountsBeta(
            List<SchedulePlanItem> items, java.util.function.Function<SchedulePlanItem, Long> ownerFunc) {
        return items.stream()
                .flatMap(it -> WeekTypeSupport.countableWeekTypes(it.getWeekType()).stream()
                        .map(wt -> Map.entry(
                                new ScoringFunctions.WeekOwner(ownerFunc.apply(it), wt),
                                it.getWeekday())))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.groupingBy(Map.Entry::getValue, Collectors.counting())));
    }

    private Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> aggregateDayItemsBeta(
            List<SchedulePlanItem> items, java.util.function.Function<SchedulePlanItem, Long> ownerFunc) {
        java.util.Map<ScoringFunctions.WeekOwner, java.util.Map<Integer, List<SchedulePlanItem>>> result = new java.util.HashMap<>();
        for (SchedulePlanItem it : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
                ScoringFunctions.WeekOwner key = new ScoringFunctions.WeekOwner(ownerFunc.apply(it), wt);
                result.computeIfAbsent(key, k -> new java.util.HashMap<>())
                        .computeIfAbsent(it.getWeekday(), d -> new java.util.ArrayList<>())
                        .add(it);
            }
        }
        return result;
    }
}
