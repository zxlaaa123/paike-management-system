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
        private final double classVariancePenaltySum;
        private final double teacherVariancePenaltySum;
        private final long duplicateCourseDays;
        private final double continuousPenaltySum;
        private final int continuousSampleCount;
        private final long roomUseSumSquares;
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
            this.classVariancePenaltySum = variancePenaltySum(classDayCounts);
            this.teacherVariancePenaltySum = variancePenaltySum(teacherDayCounts);
            this.duplicateCourseDays = courseDayCounts.values().stream().filter(count -> count > 1).count();
            this.continuousPenaltySum = continuousPenaltySum(teacherDayItems);
            this.continuousSampleCount = continuousSampleCount(teacherDayItems);
            this.roomUseSumSquares = roomUseCounts.values().stream()
                    .mapToLong(count -> count * count)
                    .sum();
            this.classDailyBalancePenalty = variancePenalty(classVariancePenaltySum, classDayCounts.size());
            this.teacherDailyLoadPenalty = variancePenalty(teacherVariancePenaltySum, teacherDayCounts.size());
            this.courseDistributionPenalty = duplicateCoursePenalty(duplicateCourseDays, courseDayCounts.size());
            this.continuousPeriodLimitPenalty = continuousPenalty(continuousPenaltySum, continuousSampleCount);
            this.classroomUtilizationPenalty = classroomPenalty(itemCount, roomUseCounts.size(), roomUseSumSquares);
            this.morningTheoryPriorityPenalty = morningPenalty(itemCount, afternoonCount);
        }

        public BigDecimal deltaPenalty(String ruleCode, SchedulePlanItem candidate) {
            Objects.requireNonNull(ruleCode, "ruleCode must not be null");
            Objects.requireNonNull(candidate, "candidate must not be null");

            return switch (ruleCode) {
                case CLASS_DAILY_BALANCE -> variancePenaltyAfter(
                        classDayCounts,
                        classVariancePenaltySum,
                        candidate.getClassId(),
                        candidate.getWeekday()
                ).subtract(classDailyBalancePenalty);
                case TEACHER_DAILY_LOAD -> variancePenaltyAfter(
                        teacherDayCounts,
                        teacherVariancePenaltySum,
                        candidate.getTeacherId(),
                        candidate.getWeekday()
                ).subtract(teacherDailyLoadPenalty);
                case COURSE_DISTRIBUTION -> duplicateCoursePenaltyAfter(candidate)
                        .subtract(courseDistributionPenalty);
                case CONTINUOUS_PERIOD_LIMIT -> continuousPenaltyAfter(candidate)
                        .subtract(continuousPeriodLimitPenalty);
                case CLASSROOM_UTILIZATION -> classroomPenaltyAfter(candidate)
                        .subtract(classroomUtilizationPenalty);
                case MORNING_THEORY_PRIORITY -> morningPenalty(
                        itemCount + 1,
                        afternoonCount + (isAfternoon(candidate, afternoonStartPeriod) ? 1 : 0)
                ).subtract(morningTheoryPriorityPenalty);
                default -> BigDecimal.ZERO;
            };
        }

        private BigDecimal variancePenaltyAfter(
                Map<Long, Map<Integer, Long>> countsByOwner,
                double penaltySum,
                Long ownerId,
                Integer weekday
        ) {
            Map<Integer, Long> beforeCounts = countsByOwner.get(ownerId);
            double beforeOwnerPenalty = ownerVariancePenalty(beforeCounts);
            double afterOwnerPenalty = ownerVariancePenaltyAfter(beforeCounts, weekday);
            int ownerCount = countsByOwner.containsKey(ownerId) ? countsByOwner.size() : countsByOwner.size() + 1;
            return variancePenalty(penaltySum - beforeOwnerPenalty + afterOwnerPenalty, ownerCount);
        }

        private BigDecimal duplicateCoursePenaltyAfter(SchedulePlanItem candidate) {
            long beforeCount = courseDayCounts.getOrDefault(courseDayKey(candidate), 0L);
            long afterDuplicateDays = duplicateCourseDays + (beforeCount == 1L ? 1L : 0L);
            int afterTotalDays = courseDayCounts.size() + (beforeCount == 0L ? 1 : 0);
            return duplicateCoursePenalty(afterDuplicateDays, afterTotalDays);
        }

        private BigDecimal continuousPenaltyAfter(SchedulePlanItem candidate) {
            List<SchedulePlanItem> beforeItems = teacherDayItems
                    .getOrDefault(candidate.getTeacherId(), Map.of())
                    .get(candidate.getWeekday());
            double beforeSamplePenalty = continuousSamplePenalty(beforeItems);
            double afterSamplePenalty = continuousSamplePenaltyAfter(beforeItems, candidate);
            int afterSampleCount = continuousSampleCount + (beforeItems == null ? 1 : 0);
            return continuousPenalty(continuousPenaltySum - beforeSamplePenalty + afterSamplePenalty, afterSampleCount);
        }

        private BigDecimal classroomPenaltyAfter(SchedulePlanItem candidate) {
            long beforeCount = roomUseCounts.getOrDefault(candidate.getClassroomId(), 0L);
            int afterRoomCount = roomUseCounts.size() + (beforeCount == 0L ? 1 : 0);
            long afterSumSquares = roomUseSumSquares - beforeCount * beforeCount + (beforeCount + 1) * (beforeCount + 1);
            return classroomPenalty(itemCount + 1, afterRoomCount, afterSumSquares);
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

    private static double variancePenaltySum(Map<Long, Map<Integer, Long>> countsByOwner) {
        return countsByOwner.values().stream()
                .mapToDouble(DeltaPenaltyScorer::ownerVariancePenalty)
                .sum();
    }

    private static BigDecimal variancePenalty(double penaltySum, int ownerCount) {
        if (ownerCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(penaltySum / ownerCount).setScale(4, RoundingMode.HALF_UP);
    }

    private static double ownerVariancePenalty(Map<Integer, Long> dayCounts) {
        if (dayCounts == null || dayCounts.size() <= 1) {
            return 0D;
        }
        double avg = dayCounts.values().stream().mapToLong(Long::longValue).average().orElse(0D);
        double variance = dayCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - avg, 2))
                .average()
                .orElse(0D);
        return Math.min(1D, variance / 4D);
    }

    private static double ownerVariancePenaltyAfter(Map<Integer, Long> beforeCounts, Integer weekday) {
        Map<Integer, Long> afterCounts = new HashMap<>(beforeCounts == null ? Map.of() : beforeCounts);
        afterCounts.merge(weekday, 1L, Long::sum);
        return ownerVariancePenalty(afterCounts);
    }

    private static BigDecimal duplicateCoursePenalty(long duplicateDays, int totalDays) {
        if (totalDays == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((double) duplicateDays / totalDays).setScale(4, RoundingMode.HALF_UP);
    }

    private static double continuousPenaltySum(Map<Long, Map<Integer, List<SchedulePlanItem>>> teacherDayItems) {
        return teacherDayItems.values().stream()
                .flatMap(dayItems -> dayItems.values().stream())
                .mapToDouble(DeltaPenaltyScorer::continuousSamplePenalty)
                .sum();
    }

    private static int continuousSampleCount(Map<Long, Map<Integer, List<SchedulePlanItem>>> teacherDayItems) {
        return teacherDayItems.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    private static BigDecimal continuousPenalty(double penaltySum, int sampleCount) {
        if (sampleCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(penaltySum / sampleCount).setScale(4, RoundingMode.HALF_UP);
    }

    private static double continuousSamplePenalty(List<SchedulePlanItem> items) {
        if (items == null || items.isEmpty()) {
            return 0D;
        }
        List<Integer> starts = items.stream()
                .map(SchedulePlanItem::getStartPeriod)
                .sorted()
                .toList();
        return continuousStartsPenalty(starts);
    }

    private static double continuousSamplePenaltyAfter(List<SchedulePlanItem> beforeItems, SchedulePlanItem candidate) {
        List<Integer> starts = new ArrayList<>();
        if (beforeItems != null) {
            starts.addAll(beforeItems.stream().map(SchedulePlanItem::getStartPeriod).toList());
        }
        starts.add(candidate.getStartPeriod());
        starts.sort(Integer::compareTo);
        return continuousStartsPenalty(starts);
    }

    private static double continuousStartsPenalty(List<Integer> starts) {
        int consecutiveChains = 0;
        for (int i = 1; i < starts.size(); i++) {
            if (starts.get(i) - starts.get(i - 1) == 2) {
                consecutiveChains++;
            }
        }
        return Math.min(1D, consecutiveChains / 2D);
    }

    private static BigDecimal classroomPenalty(int totalItems, int roomCount, long sumSquares) {
        if (totalItems <= 0 || roomCount == 0) {
            return BigDecimal.ZERO;
        }
        double avg = (double) totalItems / roomCount;
        double variance = (double) sumSquares / roomCount - avg * avg;
        return BigDecimal.valueOf(Math.min(1D, variance / Math.max(1D, avg * avg))).setScale(4, RoundingMode.HALF_UP);
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
