package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 评分体系的纯函数集合 —— 双轨制评分都在这里。
 *
 * <h2>双轨说明</h2>
 * 同名规则码（如 CLASSROOM_UTILIZATION）在<b>在线 candidateXxx</b>（贪心选候选）和
 * <b>离线 penaltyXxx</b>（rescore 写库）里用了不同公式，原因和明细见
 * {@link ScoringDimensions#ONLINE_SOFT} 与 {@link ScoringDimensions#OFFLINE_SOFT}。
 * 本类只把散在两个 service 私有作用域的实现搬过来
 * 集中、便于阅读"同一规则码两套实现"的对比，<b>行为零变更</b>。
 *
 * <h2>签名约定</h2>
 * <ul>
 *   <li><b>在线 candidateXxx</b>：返回 {@code double}（贪心选候选不需要高精度，要快）</li>
 *   <li><b>离线 penaltyXxx</b>：返回 {@code BigDecimal}（写库要严格精度，scale=4）</li>
 * </ul>
 * 不要混用。两套刻意分开，让阅读者一眼看出在线/离线身份。
 *
 * <h2>V9 阶段 2A β 评分（独立计数）</h2>
 * 聚合维度从 {@code (owner/day)} 扩展为 {@code (owner/day/weekType)}（V9_00 §5 β 裁决）。
 * 实现<b>不改动旧签名</b>（引擎 {@link com.paike.scheduler.engine.optimize.ObjectiveFunction}
 * / {@link com.paike.scheduler.engine.optimize.IncrementalPenaltyState} 仍按纯 ALL 世界调用旧签名，
 * 阶段 3 激活引擎 β 时才切换），而是<b>新增 weekType 维度重载</b>：
 * <ul>
 *   <li>用 {@link WeekOwner}（ownerId + weekType 复合键）替换裸 {@code Long ownerId}</li>
 *   <li>调用方（ScheduleScoreService / DeltaPenaltyScorer）在聚合上游用
 *       {@link com.paike.scheduler.service.WeekTypeSupport#countableWeekTypes(String)}
 *       把 ALL 展开成 [ODD, EVEN] 两条虚拟记录</li>
 * </ul>
 * 关键性质：纯 ALL 数据展开后 ODD/EVEN 两桶完全对称，归一化后数值与旧签名<b>完全相同</b>，
 * 现有 baseline 零回归（已在 ScheduleScoreServiceTest 手算验证）。
 */
public final class ScoringFunctions {

    private ScoringFunctions() {}

    // ============================================================
    // 在线评分（candidateXxx） —— V3ScheduleGenerateService.scoreCandidate 调用
    // ============================================================

    /**
     * 教室利用率（在线）：{@code studentCount / capacity}。
     * 超容也允许 >1，贪心更偏好『刚好坐满』的房间。范围 [0, N)。
     */
    public static double candidateClassroomUtilization(Classroom room, int studentCount) {
        if (room.getCapacity() == null || room.getCapacity() <= 0) {
            return 0D;
        }
        double ratio = (double) studentCount / room.getCapacity();
        return Math.max(0D, ratio);
    }

    /**
     * 班级/教师每日均衡（在线）：{@code 1 / (1 + 同 owner 当天已排数)}，范围 (0, 1]。
     * 谓词决定 owner（按 classId 或 teacherId）。越往后排越不愿意再加。
     */
    public static double candidateBalance(
            List<SchedulePlanItem> generatedItems,
            Predicate<SchedulePlanItem> ownerPredicate,
            int dayOfWeek
    ) {
        long count = generatedItems.stream()
                .filter(ownerPredicate)
                .filter(item -> Objects.equals(item.getWeekday(), dayOfWeek))
                .count();
        return 1D / (1D + count);
    }

    /**
     * 课程分布均衡（在线）：同班同课同日 ? 0 : 1，二值。
     */
    public static double candidateCourseDistribution(
            List<SchedulePlanItem> generatedItems,
            TeachingTask task,
            int dayOfWeek
    ) {
        boolean existsSameDay = generatedItems.stream().anyMatch(item ->
                Objects.equals(item.getClassId(), task.getClassId())
                        && Objects.equals(item.getCourseId(), task.getCourseId())
                        && Objects.equals(item.getWeekday(), dayOfWeek));
        return existsSameDay ? 0D : 1D;
    }

    /**
     * 连续上课限制（在线）：相邻节次 (|Δperiod|==2) 且同教师/同班 ? 0 : 1，二值。
     */
    public static double candidateContinuousLimit(
            List<SchedulePlanItem> generatedItems,
            TeachingTask task,
            TimeSlot slot
    ) {
        boolean adjacent = generatedItems.stream().anyMatch(item ->
                Objects.equals(item.getWeekday(), slot.getDayOfWeek())
                        && (Objects.equals(item.getTeacherId(), task.getTeacherId())
                                || Objects.equals(item.getClassId(), task.getClassId()))
                        && Math.abs(item.getStartPeriod() - slotToStartPeriod(slot)) == 2);
        return adjacent ? 0D : 1D;
    }

    /**
     * 理论课优先上午（在线）：非 EXPERIMENT/COMPUTER 且 periodNo<=2 ? 1 : 0，二值。
     */
    public static double candidateMorningPriority(String courseType, TimeSlot slot) {
        boolean theory = !CourseType.EXPERIMENT.getCode().equals(courseType)
                && !CourseType.COMPUTER.getCode().equals(courseType);
        return theory && slot.getPeriodNo() <= 2 ? 1D : 0D;
    }

    /**
     * 把 slot.periodNo 映射到节次起始 period（1->1, 2->3, 3->5, 4->7，其余 2N-1）。
     * 仅 candidateContinuousLimit 使用；提到 public 静态便于单测。
     */
    public static int slotToStartPeriod(TimeSlot slot) {
        return switch (slot.getPeriodNo()) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            case 4 -> 7;
            default -> Math.max(1, slot.getPeriodNo() * 2 - 1);
        };
    }

    // ============================================================
    // 离线评分（penaltyXxx） —— ScheduleScoreService.buildScoreContext 调用
    // 全部返回 BigDecimal，scale=4，HALF_UP；空集统一返 ZERO（不带 scale，由调用方 setScale）
    // ============================================================

    /**
     * 班级/教师每日数方差归一惩罚（离线）：跨日方差均值，范围 [0, 1]。
     * 班级日均衡 + 教师日负载共用。
     */
    public static BigDecimal penaltyVariance(Map<Long, Map<Integer, Long>> countsByOwner) {
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

    /**
     * 课程重复天数占比（离线）：(同班同课同日次数>1 的天数) / 总(班×课×天)。
     */
    public static BigDecimal penaltyDuplicateCourse(Map<String, Long> courseDayCounts) {
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

    /**
     * 教师连续上课链平均惩罚（离线）：教师每日 startPeriod 排序后相邻差==2 的链数，
     * min(1, chains/2) 求样本均值。范围 [0, 1]。
     */
    public static BigDecimal penaltyContinuous(Map<Long, Map<Integer, List<SchedulePlanItem>>> teacherDayItems) {
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

    /**
     * 启用教室使用次数方差归一惩罚（离线）：min(1, variance / avg²)。未使用教室按 0 次计，范围 [0, 1]。
     */
    public static BigDecimal penaltyClassroomUtilization(Map<Long, Long> roomUseCounts, int totalItems) {
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

    /**
     * 下午课占比惩罚（离线，理论课优先上午）：item.startPeriod >= afternoonStartPeriod 的占比。
     * <b>不区分课程类型</b>（这是离线公式与在线公式的差异，见 ScoringDimensions.OFFLINE_SOFT）。
     * afternoonStartPeriod 由调用方从 ScheduleThresholdProperties 注入。
     */
    public static BigDecimal penaltyMorningPriority(List<SchedulePlanItem> items, int afternoonStartPeriod) {
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long afternoonCount = items.stream()
                .filter(item -> item.getStartPeriod() >= afternoonStartPeriod)
                .count();
        return BigDecimal.valueOf((double) afternoonCount / items.size()).setScale(4, RoundingMode.HALF_UP);
    }

    // ============================================================
    // V9 阶段 2A β 维度重载（weekType 独立计数） —— 不删旧签名，引擎继续用旧签名
    // 调用方（ScheduleScoreService / DeltaPenaltyScorer）已用 WeekTypeSupport.countableWeekTypes
    // 把 ALL 展开成 [ODD, EVEN]，此处只负责按复合 key 算 penalty，公式与旧签名完全一致。
    // ============================================================

    /**
     * β 评分的复合 owner 键：ownerId（教师或班级）× weekType（ODD/EVEN）。
     * 由调用方聚合时构造，ALL 已在上游展开为 (ownerId, ODD) + (ownerId, EVEN) 两个独立键。
     */
    public record WeekOwner(Long ownerId, String weekType) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WeekOwner that)) return false;
            return Objects.equals(ownerId, that.ownerId) && Objects.equals(weekType, that.weekType);
        }
        @Override
        public int hashCode() {
            return Objects.hash(ownerId, weekType);
        }
    }

    /**
     * β 版（weekType 独立计数）：班级/教师每日数方差归一惩罚。
     * 公式与 {@link #penaltyVariance(Map)} 完全一致，仅外层 key 从 {@code Long ownerId}
     * 换成 {@link WeekOwner}（ownerId × weekType）。空集统一返 ZERO。
     */
    public static BigDecimal penaltyVarianceBeta(Map<WeekOwner, Map<Integer, Long>> countsByOwnerWeek) {
        if (countsByOwnerWeek.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double penalty = 0D;
        for (Map<Integer, Long> dayCounts : countsByOwnerWeek.values()) {
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
        double normalized = penalty / countsByOwnerWeek.size();
        return BigDecimal.valueOf(normalized).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * β 版（weekType 独立计数）：教师连续上课链平均惩罚。
     * 公式与 {@link #penaltyContinuous(Map)} 完全一致，仅外层 key 从 {@code Long teacherId}
     * 换成 {@link WeekOwner}（teacherId × weekType）。空集统一返 ZERO。
     */
    public static BigDecimal penaltyContinuousBeta(Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> teacherDayItemsWeek) {
        if (teacherDayItemsWeek.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double penalty = 0D;
        int sampleCount = 0;
        for (Map<Integer, List<SchedulePlanItem>> dayItems : teacherDayItemsWeek.values()) {
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

    /**
     * β 版（weekType 独立计数）：班级每日空堂（gap）平均惩罚。
     * 与 {@link #penaltyContinuousBeta} 对称——continuous 罚『挨太紧』，gap 罚『空太开』。
     * <p>每个 (class, weekType, day) 样本：课按 startPeriod 排序，相邻对空档
     * {@code Σ max(0, nextStart - prevStart - 2)}（每节占 2 period，相邻大节 Δ=2 无空档），
     * 样本惩罚 {@code min(1, totalGap / 4)}（约 2 个大节空档封顶），对所有样本求均值。范围 [0, 1]。
     * 空集统一返 ZERO。
     */
    public static BigDecimal penaltyClassGapBeta(Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> classDayItemsWeek) {
        if (classDayItemsWeek.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double penalty = 0D;
        int sampleCount = 0;
        for (Map<Integer, List<SchedulePlanItem>> dayItems : classDayItemsWeek.values()) {
            for (List<SchedulePlanItem> items : dayItems.values()) {
                sampleCount++;
                penalty += classDayGapSamplePenalty(items.stream()
                        .map(SchedulePlanItem::getStartPeriod)
                        .toList());
            }
        }
        if (sampleCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(penalty / sampleCount).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 单个 (class, day) 样本的空堂惩罚：startPeriod 排序后相邻对空档求和，归一 {@code min(1, gap/4)}。
     * 在线/离线/引擎增量三方共用，保证 gap 公式同源。
     */
    public static double classDayGapSamplePenalty(List<Integer> startPeriods) {
        if (startPeriods == null || startPeriods.size() <= 1) {
            return 0D;
        }
        List<Integer> sorted = startPeriods.stream().sorted().toList();
        int totalGap = 0;
        for (int i = 1; i < sorted.size(); i++) {
            totalGap += Math.max(0, sorted.get(i) - sorted.get(i - 1) - 2);
        }
        return Math.min(1D, totalGap / 4D);
    }

    /**
     * 班级空堂（在线）：候选课加入后，与同班同天已排课形成的新增空档越小越好。
     * 返回 {@code 1 / (1 + 新增空档)}，范围 (0, 1]；同班同天首节或紧邻填充返 1（最优）。
     * 在线为贪心偏好信号（双轨制，公式与离线 β 不必逐位一致，见 ScoringDimensions）。
     */
    public static double candidateClassGap(
            List<SchedulePlanItem> generatedItems,
            Long classId,
            int dayOfWeek,
            int candidateStartPeriod
    ) {
        List<Integer> starts = generatedItems.stream()
                .filter(item -> Objects.equals(item.getClassId(), classId))
                .filter(item -> Objects.equals(item.getWeekday(), dayOfWeek))
                .map(SchedulePlanItem::getStartPeriod)
                .toList();
        if (starts.isEmpty()) {
            return 1D;
        }
        double before = classDayGapSamplePenalty(starts);
        List<Integer> after = new java.util.ArrayList<>(starts);
        after.add(candidateStartPeriod);
        double afterPenalty = classDayGapSamplePenalty(after);
        double introduced = Math.max(0D, afterPenalty - before);
        return 1D / (1D + introduced);
    }
}
