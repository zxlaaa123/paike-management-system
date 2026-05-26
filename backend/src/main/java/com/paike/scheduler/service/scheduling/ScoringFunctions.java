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
}
