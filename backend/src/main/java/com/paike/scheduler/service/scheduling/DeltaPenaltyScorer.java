package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.entity.SchedulePlanItem;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        Map<String, BigDecimal> weights = weightMap == null ? Map.of() : weightMap;
        BigDecimal total = BigDecimal.ZERO;
        for (String ruleCode : SOFT_RULE_CODES) {
            BigDecimal weight = weights.getOrDefault(ruleCode, BigDecimal.ZERO);
            if (weight.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            total = total.add(weight.multiply(deltaPenalty(ruleCode, currentItems, candidate, afternoonStartPeriod)));
        }
        return total;
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
                item -> item.getClassId() + "_" + item.getCourseId() + "_" + item.getWeekday(),
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
}

