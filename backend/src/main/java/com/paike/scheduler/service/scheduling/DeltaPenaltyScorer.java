package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.entity.SchedulePlanItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes offline-formula penalty deltas for a candidate item.
 * <p>
 * This is intentionally not wired into V3 generation yet. Phase 1 only
 * establishes the pure calculation model used by future -DeltaPenalty scoring.
 */
public final class DeltaPenaltyScorer {

    public static final String CLASS_DAILY_BALANCE = "CLASS_DAILY_BALANCE";
    public static final String TEACHER_DAILY_LOAD = "TEACHER_DAILY_LOAD";
    public static final String COURSE_DISTRIBUTION = "COURSE_DISTRIBUTION";
    public static final String CONTINUOUS_PERIOD_LIMIT = "CONTINUOUS_PERIOD_LIMIT";
    public static final String CLASSROOM_UTILIZATION = "CLASSROOM_UTILIZATION";
    public static final String MORNING_THEORY_PRIORITY = "MORNING_THEORY_PRIORITY";

    public static final List<String> SOFT_RULE_CODES = List.of(
            CLASS_DAILY_BALANCE,
            TEACHER_DAILY_LOAD,
            COURSE_DISTRIBUTION,
            CONTINUOUS_PERIOD_LIMIT,
            CLASSROOM_UTILIZATION,
            MORNING_THEORY_PRIORITY
    );

    private DeltaPenaltyScorer() {}

    public static PenaltyContext context(List<SchedulePlanItem> currentItems, int afternoonStartPeriod) {
        List<SchedulePlanItem> items = currentItems == null ? List.of() : currentItems;
        int afternoonCount = (int) items.stream()
                .filter(item -> isAfternoon(item, afternoonStartPeriod))
                .count();
        return new PenaltyContext(
                afternoonStartPeriod,
                items.size(),
                afternoonCount,
                nestedDayCounts(items, SchedulePlanItem::getClassId),
                nestedDayCounts(items, SchedulePlanItem::getTeacherId),
                courseDayCounts(items),
                nestedDayItems(items, SchedulePlanItem::getTeacherId),
                roomUseCounts(items)
        );
    }

    /**
     * Returns penalty(after adding candidate) - penalty(current).
     */
    public static BigDecimal deltaPenalty(
            String ruleCode,
            List<SchedulePlanItem> currentItems,
            SchedulePlanItem candidate,
            int afternoonStartPeriod
    ) {
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");

        List<SchedulePlanItem> before = currentItems == null ? List.of() : currentItems;
        List<SchedulePlanItem> after = withCandidate(before, candidate);
        return penalty(ruleCode, after, afternoonStartPeriod)
                .subtract(penalty(ruleCode, before, afternoonStartPeriod));
    }

    /**
     * Sums weight(rule) * deltaPenalty(rule) for all supported soft rules.
     */
    public static BigDecimal weightedSoftDeltaPenalty(
            Map<String, BigDecimal> weightMap,
            List<SchedulePlanItem> currentItems,
            SchedulePlanItem candidate,
            int afternoonStartPeriod
    ) {
        return weightedSoftDeltaPenalty(weightMap, context(currentItems, afternoonStartPeriod), candidate);
    }

    public static BigDecimal weightedSoftDeltaPenalty(
            Map<String, BigDecimal> weightMap,
            PenaltyContext context,
            SchedulePlanItem candidate
    ) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");

        Map<String, BigDecimal> weights = weightMap == null ? Map.of() : weightMap;
        BigDecimal total = BigDecimal.ZERO;
        for (String ruleCode : SOFT_RULE_CODES) {
            BigDecimal weight = weights.getOrDefault(ruleCode, BigDecimal.ZERO);
            if (weight.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            total = total.add(weight.multiply(context.deltaPenalty(ruleCode, candidate)));
        }
        return total;
    }

    public static final class PenaltyContext {
        private final int afternoonStartPeriod;
        private final int itemCount;
        private final int afternoonCount;
        private final Map<Long, Map<Integer, Long>> classDayCounts;
        private final Map<Long, Map<Integer, Long>> teacherDayCounts;
        private final Map<String, Long> courseDayCounts;
        private final Map<Long, Map<Integer, List<SchedulePlanItem>>> teacherDayItems;
        private final Map<Long, Long> roomUseCounts;
        private final BigDecimal classDailyBalancePenalty;
        private final BigDecimal teacherDailyLoadPenalty;
        private final BigDecimal courseDistributionPenalty;
        private final BigDecimal continuousPeriodLimitPenalty;
        private final BigDecimal classroomUtilizationPenalty;
        private final BigDecimal morningTheoryPriorityPenalty;

        private PenaltyContext(
                int afternoonStartPeriod,
                int itemCount,
                int afternoonCount,
                Map<Long, Map<Integer, Long>> classDayCounts,
                Map<Long, Map<Integer, Long>> teacherDayCounts,
                Map<String, Long> courseDayCounts,
                Map<Long, Map<Integer, List<SchedulePlanItem>>> teacherDayItems,
                Map<Long, Long> roomUseCounts
        ) {
            this.afternoonStartPeriod = afternoonStartPeriod;
            this.itemCount = itemCount;
            this.afternoonCount = afternoonCount;
            this.classDayCounts = classDayCounts;
            this.teacherDayCounts = teacherDayCounts;
            this.courseDayCounts = courseDayCounts;
            this.teacherDayItems = teacherDayItems;
            this.roomUseCounts = roomUseCounts;
            this.classDailyBalancePenalty = ScoringFunctions.penaltyVariance(classDayCounts);
            this.teacherDailyLoadPenalty = ScoringFunctions.penaltyVariance(teacherDayCounts);
            this.courseDistributionPenalty = ScoringFunctions.penaltyDuplicateCourse(courseDayCounts);
            this.continuousPeriodLimitPenalty = ScoringFunctions.penaltyContinuous(teacherDayItems);
            this.classroomUtilizationPenalty = ScoringFunctions.penaltyClassroomUtilization(roomUseCounts, itemCount);
            this.morningTheoryPriorityPenalty = morningPenalty(itemCount, afternoonCount);
        }

        public BigDecimal deltaPenalty(String ruleCode, SchedulePlanItem candidate) {
            Objects.requireNonNull(ruleCode, "ruleCode must not be null");
            Objects.requireNonNull(candidate, "candidate must not be null");

            return switch (ruleCode) {
                case CLASS_DAILY_BALANCE -> ScoringFunctions.penaltyVariance(
                        withNestedDayCount(classDayCounts, candidate.getClassId(), candidate.getWeekday())
                ).subtract(classDailyBalancePenalty);
                case TEACHER_DAILY_LOAD -> ScoringFunctions.penaltyVariance(
                        withNestedDayCount(teacherDayCounts, candidate.getTeacherId(), candidate.getWeekday())
                ).subtract(teacherDailyLoadPenalty);
                case COURSE_DISTRIBUTION -> ScoringFunctions.penaltyDuplicateCourse(
                        withCount(courseDayCounts, courseDayKey(candidate))
                ).subtract(courseDistributionPenalty);
                case CONTINUOUS_PERIOD_LIMIT -> ScoringFunctions.penaltyContinuous(
                        withNestedDayItem(teacherDayItems, candidate.getTeacherId(), candidate.getWeekday(), candidate)
                ).subtract(continuousPeriodLimitPenalty);
                case CLASSROOM_UTILIZATION -> ScoringFunctions.penaltyClassroomUtilization(
                        withCount(roomUseCounts, candidate.getClassroomId()), itemCount + 1
                ).subtract(classroomUtilizationPenalty);
                case MORNING_THEORY_PRIORITY -> morningPenalty(
                        itemCount + 1,
                        afternoonCount + (isAfternoon(candidate, afternoonStartPeriod) ? 1 : 0)
                ).subtract(morningTheoryPriorityPenalty);
                default -> BigDecimal.ZERO;
            };
        }
    }

    private static BigDecimal penalty(String ruleCode, List<SchedulePlanItem> items, int afternoonStartPeriod) {
        return switch (ruleCode) {
            case CLASS_DAILY_BALANCE -> ScoringFunctions.penaltyVariance(nestedDayCounts(items, SchedulePlanItem::getClassId));
            case TEACHER_DAILY_LOAD -> ScoringFunctions.penaltyVariance(nestedDayCounts(items, SchedulePlanItem::getTeacherId));
            case COURSE_DISTRIBUTION -> ScoringFunctions.penaltyDuplicateCourse(courseDayCounts(items));
            case CONTINUOUS_PERIOD_LIMIT -> ScoringFunctions.penaltyContinuous(nestedDayItems(items, SchedulePlanItem::getTeacherId));
            case CLASSROOM_UTILIZATION -> ScoringFunctions.penaltyClassroomUtilization(roomUseCounts(items), items.size());
            case MORNING_THEORY_PRIORITY -> ScoringFunctions.penaltyMorningPriority(items, afternoonStartPeriod);
            default -> BigDecimal.ZERO;
        };
    }

    private static List<SchedulePlanItem> withCandidate(List<SchedulePlanItem> currentItems, SchedulePlanItem candidate) {
        List<SchedulePlanItem> result = new ArrayList<>(currentItems.size() + 1);
        result.addAll(currentItems);
        result.add(candidate);
        return result;
    }

    private static Map<Long, Map<Integer, Long>> nestedDayCounts(
            List<SchedulePlanItem> items,
            Function<SchedulePlanItem, Long> ownerExtractor
    ) {
        return items.stream().collect(Collectors.groupingBy(
                ownerExtractor,
                Collectors.groupingBy(SchedulePlanItem::getWeekday, Collectors.counting())
        ));
    }

    private static Map<String, Long> courseDayCounts(List<SchedulePlanItem> items) {
        return items.stream().collect(Collectors.groupingBy(
                DeltaPenaltyScorer::courseDayKey,
                Collectors.counting()
        ));
    }

    private static Map<Long, Map<Integer, List<SchedulePlanItem>>> nestedDayItems(
            List<SchedulePlanItem> items,
            Function<SchedulePlanItem, Long> ownerExtractor
    ) {
        return items.stream().collect(Collectors.groupingBy(
                ownerExtractor,
                Collectors.groupingBy(SchedulePlanItem::getWeekday)
        ));
    }

    private static Map<Long, Long> roomUseCounts(List<SchedulePlanItem> items) {
        return items.stream().collect(Collectors.groupingBy(SchedulePlanItem::getClassroomId, Collectors.counting()));
    }

    private static Map<Long, Map<Integer, Long>> withNestedDayCount(
            Map<Long, Map<Integer, Long>> source,
            Long ownerId,
            Integer weekday
    ) {
        Map<Long, Map<Integer, Long>> result = new HashMap<>(source);
        Map<Integer, Long> dayCounts = new HashMap<>(result.getOrDefault(ownerId, Map.of()));
        dayCounts.merge(weekday, 1L, Long::sum);
        result.put(ownerId, dayCounts);
        return result;
    }

    private static <K> Map<K, Long> withCount(Map<K, Long> source, K key) {
        Map<K, Long> result = new HashMap<>(source);
        result.merge(key, 1L, Long::sum);
        return result;
    }

    private static Map<Long, Map<Integer, List<SchedulePlanItem>>> withNestedDayItem(
            Map<Long, Map<Integer, List<SchedulePlanItem>>> source,
            Long ownerId,
            Integer weekday,
            SchedulePlanItem candidate
    ) {
        Map<Long, Map<Integer, List<SchedulePlanItem>>> result = new HashMap<>(source);
        Map<Integer, List<SchedulePlanItem>> dayItems = new HashMap<>(result.getOrDefault(ownerId, Map.of()));
        List<SchedulePlanItem> items = new ArrayList<>(dayItems.getOrDefault(weekday, List.of()));
        items.add(candidate);
        dayItems.put(weekday, items);
        result.put(ownerId, dayItems);
        return result;
    }

    private static String courseDayKey(SchedulePlanItem item) {
        return item.getClassId() + "_" + item.getCourseId() + "_" + item.getWeekday();
    }

    private static boolean isAfternoon(SchedulePlanItem item, int afternoonStartPeriod) {
        return item.getStartPeriod() != null && item.getStartPeriod() >= afternoonStartPeriod;
    }

    private static BigDecimal morningPenalty(int itemCount, int afternoonCount) {
        if (itemCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((double) afternoonCount / itemCount).setScale(4, RoundingMode.HALF_UP);
    }
}
