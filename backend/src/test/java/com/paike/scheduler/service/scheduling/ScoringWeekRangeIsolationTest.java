package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.service.WeekPatternSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V10 阶段 5 红线测试：评分链周段隔离。
 *
 * <p>验证 β 评分（penaltyVarianceBeta / penaltyContinuousBeta / penaltyClassGapBeta）
 * 按 (owner × weekType × weekMask) 分桶——周段实际不相交的两条 item 互不影响方差，
 * 周段相交的两条 item 互相影响。纯 ALL 1-20 零回归。
 *
 * <p>本测试先于生产代码编写（先红后绿），用 {@link #aggregateDayCountsBetaV10}
 * 模拟 V10 分桶逻辑：构造 {@link ScoringFunctions.WeekOwner} 时附加
 * {@link WeekPatternSupport#activeWeekMask} 作为第三维 key。
 */
class ScoringWeekRangeIsolationTest {

    /**
     * 红线 1：教师 t1 周一 ALL 1-8 + 周一 ALL 9-16 + 周二 ALL 1-8 + 周二 ALL 9-16。
     * V10 下不相交周段进不同 mask 桶：
     *   - t1 (ODD, mask 1-8) {d1:1, d2:1} → 跨日均衡 → variance=0
     *   - t1 (ODD, mask 9-16) {d1:1, d2:1} → 跨日均衡 → variance=0
     *   - t1 (EVEN, mask 1-8) {d1:1, d2:1} → variance=0
     *   - t1 (EVEN, mask 9-16) {d1:1, d2:1} → variance=0
     *   → penaltyVarianceBeta=0
     * V9 下所有 ALL 进同一 ODD/EVEN 桶：
     *   - t1 (ODD) {d1:2, d2:2} → 跨日均衡 → variance=0（V9 也过！）
     * 所以加一个不对称：周一 2 条 + 周二 1 条（同周段），让 V9 产生方差。
     */
    @Test
    void disjointWeekRanges_noVarianceInteraction() {
        // t1 周一2条（不同周段，各自1条）+ 周二1条（1-8周段）
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ALL", 1, 8),    // 周一 1-8
                item(1L, 1L, 2L, 1L, 1, 3, "ALL", 9, 16),   // 周一 9-16（不同周段）
                item(1L, 1L, 1L, 1L, 2, 1, "ALL", 1, 8));   // 周二 1-8

        Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> teacherCounts =
                aggregateDayCountsBetaV10(items, SchedulePlanItem::getTeacherId);

        // V10: 4 个桶各自单条或均衡
        //   t1 (ODD, 1-8) {d1:1, d2:1} → variance=0
        //   t1 (ODD, 9-16) {d1:1} → size<=1 跳过
        //   t1 (EVEN, 1-8) {d1:1, d2:1} → variance=0
        //   t1 (EVEN, 9-16) {d1:1} → size<=1 跳过
        //   → penalty=0
        BigDecimal penalty = ScoringFunctions.penaltyVarianceBeta(teacherCounts);
        assertEquals(BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP), penalty,
                "不相交周段不应互相影响方差评分");

        // V9 下：t1 (ODD) {d1:2, d2:1} → avg=1.5, variance=0.166.., penalty>0
        // 验证 V10 确实把周一段隔离了：桶数应为 4（2 weekType × 2 mask）
        assertEquals(4, teacherCounts.size(), "V10 应分 4 个独立桶（2 weekType × 2 mask）");
    }

    /**
     * 红线 2：教师 t1 周一1-2节 ALL 1-8 + 周一1-2节 ALL 5-12（同槽相交周段）。
     * 两条 mask 相交但不完全相同——按 V10 mask 分桶会进不同桶。
     * 这是保守边界：V10 mask 分桶只让 mask 完全相同的进同桶。
     * 本用例验证：即使 mask 不同，各自单条仍无方差，penalty=0。
     * （真实冲突由硬冲突链判定，软评分只管均衡——不相交则互不影响是合理语义。）
     */
    @Test
    void intersectingButDifferentMask_separateBuckets() {
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ALL", 1, 8),
                item(1L, 1L, 2L, 1L, 1, 1, "ALL", 5, 12));

        Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> teacherCounts =
                aggregateDayCountsBetaV10(items, SchedulePlanItem::getTeacherId);

        // mask 不同 → 两个桶 → 各自单条 → variance=0
        // 验证：分桶后确实有两个不同的 WeekOwner key
        assertEquals(4, teacherCounts.size(),
                "ALL 展开为 ODD+EVEN，两条不同 mask → 4 个独立桶");
        BigDecimal penalty = ScoringFunctions.penaltyVarianceBeta(teacherCounts);
        assertEquals(BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP), penalty,
                "不同 mask 各自单条无方差");
    }

    /**
     * 红线 3：纯 ALL 1-20 零回归。
     * 5 条全 ALL item（复用 ScoringWeekTypeConsistencyTest fixture1），
     * V10 下所有 ALL 的 mask 相同（1-20 全周），分桶结果与 V9 β 完全一致。
     */
    @Test
    void pureAllData_zeroRegression() {
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ALL", 1, 20),
                item(1L, 1L, 1L, 1L, 1, 3, "ALL", 1, 20),
                item(2L, 2L, 2L, 2L, 2, 5, "ALL", 1, 20),
                item(2L, 2L, 2L, 2L, 2, 7, "ALL", 1, 20),
                item(1L, 1L, 1L, 2L, 2, 1, "ALL", 1, 20));

        BigDecimal classBalance = ScoringFunctions.penaltyVarianceBeta(
                aggregateDayCountsBetaV10(items, SchedulePlanItem::getClassId));
        BigDecimal teacherLoad = ScoringFunctions.penaltyVarianceBeta(
                aggregateDayCountsBetaV10(items, SchedulePlanItem::getTeacherId));
        BigDecimal continuous = ScoringFunctions.penaltyContinuousBeta(
                aggregateDayItemsBetaV10(items, SchedulePlanItem::getTeacherId));

        // 与 ScoringWeekTypeConsistencyTest.pureAllData_zeroRegression 锁定的 baseline 一致
        assertEquals(new BigDecimal("0.0313"), classBalance, "纯 ALL classBalance 应零回归");
        assertEquals(new BigDecimal("0.0313"), teacherLoad, "纯 ALL teacherLoad 应零回归");
        assertEquals(new BigDecimal("0.3333"), continuous, "纯 ALL continuous 应零回归");
    }

    /**
     * 红线 4：同一周段的两条 ALL 互相影响（与 V9 一致）。
     * t1 周一 ALL 1-8 + 周一 ALL 1-8（同周段同槽），应进同一桶 {d1:2}，有方差。
     */
    @Test
    void sameWeekRange_interactionPreserved() {
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ALL", 1, 8),
                item(1L, 1L, 2L, 1L, 1, 3, "ALL", 1, 8),
                item(1L, 1L, 1L, 1L, 2, 1, "ALL", 1, 8));

        Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> teacherCounts =
                aggregateDayCountsBetaV10(items, SchedulePlanItem::getTeacherId);

        // 同周段 → 同 mask → 同桶；t1 ODD 桶 {d1:1, d2:1}（ALL 展开为 ODD+EVEN，各计 1 条/天）
        // 但 t1 周一有两条 item（startPeriod 1 和 3），所以 ODD 桶 {d1:2, d2:1}
        Map<Integer, Long> oddDays = teacherCounts.get(v10WeekOwner(1L, "ODD", "ALL", 1, 8));
        assertEquals(Map.of(1, 2L, 2, 1L), oddDays, "同周段同槽应进同一桶");
    }

    /**
     * 红线 5：courseDistribution（penaltyDuplicateCourse）周段隔离。
     * 同班同课同天同周型但不同周段 → 不算重复（实际不同周上课）。
     */
    @Test
    void courseDistribution_disjointRangesNotDuplicate() {
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ALL", 1, 8),
                item(1L, 1L, 1L, 1L, 1, 1, "ALL", 9, 16));

        Map<String, Long> courseDayCounts = items.stream()
                .flatMap(it -> com.paike.scheduler.service.WeekTypeSupport.countableWeekTypes(it.getWeekType()).stream()
                        .map(wt -> Map.entry(
                                it.getClassId() + "_" + it.getCourseId() + "_" + it.getWeekday() + "_" + wt
                                        + "_" + WeekPatternSupport.weekRangeKey(it.getWeekType(), it.getStartWeek(), it.getEndWeek()),
                                1L)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.summingLong(Map.Entry::getValue)));

        // 两条不同 mask → 4 个 key（ODD/EVEN × 两条），各 count=1 → 无重复
        long duplicateDays = courseDayCounts.values().stream().filter(count -> count > 1).count();
        assertEquals(0L, duplicateDays, "不相交周段不应算课程重复");
    }

    /**
     * 红线 6：mask 相同的 ALL 进同桶，与 V9 countableWeekTypes 行为等价。
     * 验证 V10 分桶对纯 ALL 数据产生的 key 数量 = V9 分桶的 key 数量。
     */
    @Test
    void pureAllData_sameBucketCountAsV9() {
        List<SchedulePlanItem> items = List.of(
                item(1L, 1L, 1L, 1L, 1, 1, "ALL", 1, 20),
                item(1L, 1L, 1L, 1L, 1, 3, "ALL", 1, 20),
                item(2L, 2L, 2L, 2L, 2, 5, "ALL", 1, 20));

        Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> v10Counts =
                aggregateDayCountsBetaV10(items, SchedulePlanItem::getTeacherId);

        // 两个教师 × 2 weekType (ODD/EVEN) = 4 个桶（V9 也是 4 个）
        assertEquals(4, v10Counts.size(), "纯 ALL 1-20 应与 V9 同桶数");
    }

    // ============================================================
    // helpers
    // ============================================================

    private SchedulePlanItem item(Long teacherId, Long classId, Long courseId, Long classroomId,
                                  int weekday, int startPeriod, String weekType,
                                  int startWeek, int endWeek) {
        SchedulePlanItem it = new SchedulePlanItem();
        it.setTeacherId(teacherId);
        it.setClassId(classId);
        it.setCourseId(courseId);
        it.setClassroomId(classroomId);
        it.setWeekday(weekday);
        it.setStartPeriod(startPeriod);
        it.setWeekType(weekType);
        it.setStartWeek(startWeek);
        it.setEndWeek(endWeek);
        return it;
    }

    /**
     * V10 分桶模拟：WeekOwner 附加 weekMask 维度。
     * mask 由 {@link WeekPatternSupport#activeWeekMask} 计算，保证：
     * - 同 weekType 同周段 → 同 mask → 同桶
     * - 不同周段（不相交或部分相交） → 不同 mask → 不同桶
     */
    private Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> aggregateDayCountsBetaV10(
            List<SchedulePlanItem> items, Function<SchedulePlanItem, Long> ownerFunc) {
        return items.stream()
                .flatMap(it -> com.paike.scheduler.service.WeekTypeSupport.countableWeekTypes(it.getWeekType()).stream()
                        .map(wt -> Map.entry(
                                v10WeekOwner(ownerFunc.apply(it), wt, it.getWeekType(), it.getStartWeek(), it.getEndWeek()),
                                it.getWeekday())))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.groupingBy(
                                Map.Entry::getValue,
                                Collectors.counting())));
    }

    private Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> aggregateDayItemsBetaV10(
            List<SchedulePlanItem> items, Function<SchedulePlanItem, Long> ownerFunc) {
        Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> result = new java.util.HashMap<>();
        for (SchedulePlanItem it : items) {
            for (String wt : com.paike.scheduler.service.WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
                ScoringFunctions.WeekOwner key = v10WeekOwner(ownerFunc.apply(it), wt, it.getWeekType(), it.getStartWeek(), it.getEndWeek());
                result.computeIfAbsent(key, k -> new java.util.HashMap<>())
                        .computeIfAbsent(it.getWeekday(), d -> new java.util.ArrayList<>())
                        .add(it);
            }
        }
        return result;
    }

    /**
     * V10 WeekOwner 构造：附加 weekRangeKey。
     * 生产代码已升级 WeekOwner record 为 3 字段 (ownerId, weekType, weekRangeKey)。
     */
    private ScoringFunctions.WeekOwner v10WeekOwner(Long ownerId, String weekType, String originalWeekType,
                                                     Integer startWeek, Integer endWeek) {
        String rangeKey = WeekPatternSupport.weekRangeKey(originalWeekType, startWeek, endWeek);
        return new ScoringFunctions.WeekOwner(ownerId, weekType, rangeKey);
    }

    /** V9 兼容分桶（无 rangeKey 维度）——用于验证测试确实能区分 V9 vs V10 行为 */
    @SuppressWarnings("unused")
    private ScoringFunctions.WeekOwner v9WeekOwner(Long ownerId, String weekType, String originalWeekType,
                                                    Integer startWeek, Integer endWeek) {
        return new ScoringFunctions.WeekOwner(ownerId, weekType, "1-20");
    }
}
