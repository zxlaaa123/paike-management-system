package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.service.WeekPatternSupport;
import com.paike.scheduler.service.WeekTypeSupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
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
    public static final String CLASS_GAP_PENALTY = "CLASS_GAP_PENALTY";

    public static final List<String> SOFT_RULE_CODES = List.of(
            CLASS_DAILY_BALANCE,
            TEACHER_DAILY_LOAD,
            COURSE_DISTRIBUTION,
            CONTINUOUS_PERIOD_LIMIT,
            CLASSROOM_UTILIZATION,
            MORNING_THEORY_PRIORITY,
            CLASS_GAP_PENALTY
    );

    private DeltaPenaltyScorer() {}

    public static PenaltyContext context(List<SchedulePlanItem> currentItems, int afternoonStartPeriod) {
        return context(currentItems, List.of(), afternoonStartPeriod);
    }

    public static PenaltyContext context(
            List<SchedulePlanItem> currentItems,
            Collection<Long> activeClassroomIds,
            int afternoonStartPeriod
    ) {
        List<SchedulePlanItem> items = currentItems == null ? List.of() : currentItems;
        int afternoonCount = (int) items.stream()
                .filter(item -> isAfternoon(item, afternoonStartPeriod))
                .count();
        return new PenaltyContext(
                afternoonStartPeriod,
                items.size(),
                afternoonCount,
                nestedDayCountsBeta(items, SchedulePlanItem::getClassId),
                nestedDayCountsBeta(items, SchedulePlanItem::getTeacherId),
                courseDayCountsBeta(items),
                nestedDayItemsBeta(items, SchedulePlanItem::getTeacherId),
                nestedDayItemsBeta(items, SchedulePlanItem::getClassId),
                roomUseCounts(items, activeClassroomIds)
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
        return deltaPenalty(ruleCode, currentItems, candidate, List.of(), afternoonStartPeriod);
    }

    public static BigDecimal deltaPenalty(
            String ruleCode,
            List<SchedulePlanItem> currentItems,
            SchedulePlanItem candidate,
            Collection<Long> activeClassroomIds,
            int afternoonStartPeriod
    ) {
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");

        List<SchedulePlanItem> before = currentItems == null ? List.of() : currentItems;
        List<SchedulePlanItem> after = withCandidate(before, candidate);
        return penalty(ruleCode, after, activeClassroomIds, afternoonStartPeriod)
                .subtract(penalty(ruleCode, before, activeClassroomIds, afternoonStartPeriod));
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
            List<SchedulePlanItem> currentItems,
            SchedulePlanItem candidate,
            Collection<Long> activeClassroomIds,
            int afternoonStartPeriod
    ) {
        return weightedSoftDeltaPenalty(
                weightMap,
                context(currentItems, activeClassroomIds, afternoonStartPeriod),
                candidate);
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
        // V9 阶段 2A β：owner 维度加 weekType（ALL 已展开为 ODD+EVEN 两个独立子桶）
        private final Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> classDayCounts;
        private final Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> teacherDayCounts;
        private final Map<String, Long> courseDayCounts;
        private final Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> teacherDayItems;
        private final Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> classDayItems;
        private final Map<Long, Long> roomUseCounts;
        private final double classVariancePenaltySum;
        private final double teacherVariancePenaltySum;
        private final long duplicateCourseDays;
        private final double continuousPenaltySum;
        private final int continuousSampleCount;
        private final double classGapPenaltySum;
        private final int classGapSampleCount;
        private final long roomUseSumSquares;
        private final BigDecimal classDailyBalancePenalty;
        private final BigDecimal teacherDailyLoadPenalty;
        private final BigDecimal courseDistributionPenalty;
        private final BigDecimal continuousPeriodLimitPenalty;
        private final BigDecimal classroomUtilizationPenalty;
        private final BigDecimal morningTheoryPriorityPenalty;
        private final BigDecimal classGapPenalty;

        private PenaltyContext(
                int afternoonStartPeriod,
                int itemCount,
                int afternoonCount,
                Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> classDayCounts,
                Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> teacherDayCounts,
                Map<String, Long> courseDayCounts,
                Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> teacherDayItems,
                Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> classDayItems,
                Map<Long, Long> roomUseCounts
        ) {
            this.afternoonStartPeriod = afternoonStartPeriod;
            this.itemCount = itemCount;
            this.afternoonCount = afternoonCount;
            this.classDayCounts = classDayCounts;
            this.teacherDayCounts = teacherDayCounts;
            this.courseDayCounts = courseDayCounts;
            this.teacherDayItems = teacherDayItems;
            this.classDayItems = classDayItems;
            this.roomUseCounts = roomUseCounts;
            this.classVariancePenaltySum = variancePenaltySum(classDayCounts);
            this.teacherVariancePenaltySum = variancePenaltySum(teacherDayCounts);
            this.duplicateCourseDays = courseDayCounts.values().stream().filter(count -> count > 1).count();
            this.continuousPenaltySum = continuousPenaltySum(teacherDayItems);
            this.continuousSampleCount = continuousSampleCount(teacherDayItems);
            this.classGapPenaltySum = classGapPenaltySum(classDayItems);
            this.classGapSampleCount = continuousSampleCount(classDayItems);
            this.roomUseSumSquares = roomUseCounts.values().stream()
                    .mapToLong(count -> count * count)
                    .sum();
            this.classDailyBalancePenalty = variancePenalty(classVariancePenaltySum, classDayCounts.size());
            this.teacherDailyLoadPenalty = variancePenalty(teacherVariancePenaltySum, teacherDayCounts.size());
            this.courseDistributionPenalty = duplicateCoursePenalty(duplicateCourseDays, courseDayCounts.size());
            this.continuousPeriodLimitPenalty = continuousPenalty(continuousPenaltySum, continuousSampleCount);
            this.classroomUtilizationPenalty = classroomPenalty(itemCount, roomUseCounts.size(), roomUseSumSquares);
            this.morningTheoryPriorityPenalty = morningPenalty(itemCount, afternoonCount);
            this.classGapPenalty = continuousPenalty(classGapPenaltySum, classGapSampleCount);
        }

        /**
         * V9 阶段 2A β：candidate 按 {@link WeekTypeSupport#countableWeekTypes} 展开到对应周次桶，
         * 每个受影响桶分别算 before/after delta 再汇总。ALL candidate 同时影响 ODD 与 EVEN 桶；
         * 纯 ALL 数据下两个桶对称，delta 与旧全周公式一致（零回归）。
         */
        public BigDecimal deltaPenalty(String ruleCode, SchedulePlanItem candidate) {
            Objects.requireNonNull(ruleCode, "ruleCode must not be null");
            Objects.requireNonNull(candidate, "candidate must not be null");

            return switch (ruleCode) {
                case CLASS_DAILY_BALANCE -> variancePenaltyAfterBeta(
                        classDayCounts,
                        classVariancePenaltySum,
                        candidate.getClassId(),
                        candidate.getWeekday(),
                        candidate
                ).subtract(classDailyBalancePenalty);
                case TEACHER_DAILY_LOAD -> variancePenaltyAfterBeta(
                        teacherDayCounts,
                        teacherVariancePenaltySum,
                        candidate.getTeacherId(),
                        candidate.getWeekday(),
                        candidate
                ).subtract(teacherDailyLoadPenalty);
                case COURSE_DISTRIBUTION -> duplicateCoursePenaltyAfterBeta(candidate)
                        .subtract(courseDistributionPenalty);
                case CONTINUOUS_PERIOD_LIMIT -> continuousPenaltyAfterBeta(candidate)
                        .subtract(continuousPeriodLimitPenalty);
                case CLASS_GAP_PENALTY -> classGapPenaltyAfterBeta(candidate)
                        .subtract(classGapPenalty);
                case CLASSROOM_UTILIZATION -> classroomPenaltyAfter(candidate)
                        .subtract(classroomUtilizationPenalty);
                case MORNING_THEORY_PRIORITY -> morningPenalty(
                        itemCount + 1,
                        afternoonCount + (isAfternoon(candidate, afternoonStartPeriod) ? 1 : 0)
                ).subtract(morningTheoryPriorityPenalty);
                default -> BigDecimal.ZERO;
            };
        }

        /**
         * V10 β 版：candidate 展开到每个 countableWeekType 桶，对每个受影响的 (owner,weekType,weekMask) 算 before/after。
         * weekMask 由 candidate 的实际周段决定，周段不同的 item 进不同桶互不影响。
         */
        private BigDecimal variancePenaltyAfterBeta(
                Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> countsByOwner,
                double penaltySum,
                Long ownerId,
                Integer weekday,
                SchedulePlanItem candidate
        ) {
            double sumAfter = penaltySum;
            int newOwners = 0;
            for (String wt : WeekTypeSupport.countableWeekTypes(candidate.getWeekType())) {
                ScoringFunctions.WeekOwner key = weekOwner(ownerId, wt, candidate);
                Map<Integer, Long> beforeCounts = countsByOwner.get(key);
                double beforeOwnerPenalty = ownerVariancePenalty(beforeCounts);
                double afterOwnerPenalty = ownerVariancePenaltyAfter(beforeCounts, weekday);
                sumAfter = sumAfter - beforeOwnerPenalty + afterOwnerPenalty;
                if (!countsByOwner.containsKey(key)) {
                    newOwners++;
                }
            }
            return variancePenalty(sumAfter, countsByOwner.size() + newOwners);
        }

        /**
         * β 版：candidate 展开到每个 countableWeekType，分别查 courseDayCounts 桶，累计 duplicate 增量。
         */
        private BigDecimal duplicateCoursePenaltyAfterBeta(SchedulePlanItem candidate) {
            long afterDuplicateDays = duplicateCourseDays;
            int afterTotalDays = courseDayCounts.size();
            for (String wt : WeekTypeSupport.countableWeekTypes(candidate.getWeekType())) {
                String key = courseDayKeyBeta(candidate, wt);
                long beforeCount = courseDayCounts.getOrDefault(key, 0L);
                if (beforeCount == 1L) {
                    afterDuplicateDays++;
                }
                if (beforeCount == 0L) {
                    afterTotalDays++;
                }
            }
            return duplicateCoursePenalty(afterDuplicateDays, afterTotalDays);
        }

        /**
         * β 版：candidate 展开到每个 countableWeekType，对每个 (teacher,wt) 桶的该 day sample 算 before/after。
         */
        private BigDecimal continuousPenaltyAfterBeta(SchedulePlanItem candidate) {
            double afterSum = continuousPenaltySum;
            int afterSampleCount = continuousSampleCount;
            for (String wt : WeekTypeSupport.countableWeekTypes(candidate.getWeekType())) {
                ScoringFunctions.WeekOwner key = weekOwner(candidate.getTeacherId(), wt, candidate);
                Map<Integer, List<SchedulePlanItem>> dayMap = teacherDayItems.getOrDefault(key, Map.of());
                List<SchedulePlanItem> beforeItems = dayMap.get(candidate.getWeekday());
                double beforeSamplePenalty = continuousSamplePenalty(beforeItems);
                double afterSamplePenalty = continuousSamplePenaltyAfter(beforeItems, candidate);
                afterSum = afterSum - beforeSamplePenalty + afterSamplePenalty;
                if (beforeItems == null) {
                    afterSampleCount++;
                }
            }
            return continuousPenalty(afterSum, afterSampleCount);
        }

        /**
         * β 版：candidate 展开到每个 countableWeekType，对每个 (class,wt,mask) 桶的该 day sample 算 before/after。
         * 与 continuousPenaltyAfterBeta 对称，仅 owner 换成 classId、样本公式换成 gap。
         */
        private BigDecimal classGapPenaltyAfterBeta(SchedulePlanItem candidate) {
            double afterSum = classGapPenaltySum;
            int afterSampleCount = classGapSampleCount;
            for (String wt : WeekTypeSupport.countableWeekTypes(candidate.getWeekType())) {
                ScoringFunctions.WeekOwner key = weekOwner(candidate.getClassId(), wt, candidate);
                Map<Integer, List<SchedulePlanItem>> dayMap = classDayItems.getOrDefault(key, Map.of());
                List<SchedulePlanItem> beforeItems = dayMap.get(candidate.getWeekday());
                double beforeSamplePenalty = classGapSamplePenalty(beforeItems);
                double afterSamplePenalty = classGapSamplePenaltyAfter(beforeItems, candidate);
                afterSum = afterSum - beforeSamplePenalty + afterSamplePenalty;
                if (beforeItems == null) {
                    afterSampleCount++;
                }
            }
            return continuousPenalty(afterSum, afterSampleCount);
        }

        private BigDecimal classroomPenaltyAfter(SchedulePlanItem candidate) {
            long beforeCount = roomUseCounts.getOrDefault(candidate.getClassroomId(), 0L);
            int afterRoomCount = roomUseCounts.size() + (beforeCount == 0L ? 1 : 0);
            long afterSumSquares = roomUseSumSquares - beforeCount * beforeCount + (beforeCount + 1) * (beforeCount + 1);
            return classroomPenalty(itemCount + 1, afterRoomCount, afterSumSquares);
        }
    }

    private static BigDecimal penalty(
            String ruleCode,
            List<SchedulePlanItem> items,
            Collection<Long> activeClassroomIds,
            int afternoonStartPeriod
    ) {
        // V9 阶段 2A β：全量重算走 Beta 重载（ALL 展开为 ODD+EVEN）
        return switch (ruleCode) {
            case CLASS_DAILY_BALANCE -> ScoringFunctions.penaltyVarianceBeta(nestedDayCountsBeta(items, SchedulePlanItem::getClassId));
            case TEACHER_DAILY_LOAD -> ScoringFunctions.penaltyVarianceBeta(nestedDayCountsBeta(items, SchedulePlanItem::getTeacherId));
            case COURSE_DISTRIBUTION -> ScoringFunctions.penaltyDuplicateCourse(courseDayCountsBeta(items));
            case CONTINUOUS_PERIOD_LIMIT -> ScoringFunctions.penaltyContinuousBeta(nestedDayItemsBeta(items, SchedulePlanItem::getTeacherId));
            case CLASS_GAP_PENALTY -> ScoringFunctions.penaltyClassGapBeta(nestedDayItemsBeta(items, SchedulePlanItem::getClassId));
            case CLASSROOM_UTILIZATION -> ScoringFunctions.penaltyClassroomUtilization(
                    roomUseCounts(items, activeClassroomIds),
                    items.size());
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

    /**
     * V10 β 版：owner 维度加 (weekType, weekMask)，ALL 用 countableWeekTypes 展开成 ODD+EVEN 两个独立子桶，
     * 每个子桶再按实际自然周 mask 区分（周段不相交 → 不同桶 → 互不影响）。
     */
    private static Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> nestedDayCountsBeta(
            List<SchedulePlanItem> items,
            Function<SchedulePlanItem, Long> ownerExtractor
    ) {
        return items.stream()
                .flatMap(item -> WeekTypeSupport.countableWeekTypes(item.getWeekType()).stream()
                        .map(wt -> new AbstractMap.SimpleEntry<>(
                                weekOwner(ownerExtractor.apply(item), wt, item),
                                item.getWeekday())))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.groupingBy(
                                Map.Entry::getValue,
                                Collectors.counting())));
    }

    private static Map<String, Long> courseDayCountsBeta(List<SchedulePlanItem> items) {
        return items.stream()
                .flatMap(item -> WeekTypeSupport.countableWeekTypes(item.getWeekType()).stream()
                        .map(wt -> new AbstractMap.SimpleEntry<>(courseDayKeyBeta(item, wt), 1L)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.summingLong(Map.Entry::getValue)));
    }

    private static Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> nestedDayItemsBeta(
            List<SchedulePlanItem> items,
            Function<SchedulePlanItem, Long> ownerExtractor
    ) {
        Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> result = new HashMap<>();
        for (SchedulePlanItem item : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(item.getWeekType())) {
                ScoringFunctions.WeekOwner key = weekOwner(ownerExtractor.apply(item), wt, item);
                result.computeIfAbsent(key, k -> new HashMap<>())
                        .computeIfAbsent(item.getWeekday(), d -> new ArrayList<>())
                        .add(item);
            }
        }
        return result;
    }

    /** V10：构造带 weekMask 的 WeekOwner */
    private static ScoringFunctions.WeekOwner weekOwner(Long ownerId, String weekType, SchedulePlanItem item) {
        return new ScoringFunctions.WeekOwner(ownerId, weekType,
                WeekPatternSupport.weekRangeKey(item.getWeekType(), item.getStartWeek(), item.getEndWeek()));
    }

    private static Map<Long, Long> roomUseCounts(List<SchedulePlanItem> items, Collection<Long> activeClassroomIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (activeClassroomIds != null) {
            activeClassroomIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(roomId -> counts.put(roomId, 0L));
        }
        items.stream()
                .map(SchedulePlanItem::getClassroomId)
                .filter(Objects::nonNull)
                .forEach(roomId -> counts.merge(roomId, 1L, DeltaPenaltyScorer::sumLongs));
        return counts;
    }

    private static double variancePenaltySum(Map<ScoringFunctions.WeekOwner, Map<Integer, Long>> countsByOwner) {
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
        afterCounts.merge(weekday, 1L, DeltaPenaltyScorer::sumLongs);
        return ownerVariancePenalty(afterCounts);
    }

    private static Long sumLongs(Long left, Long right) {
        long safeLeft = left == null ? 0L : left;
        long safeRight = right == null ? 0L : right;
        return safeLeft + safeRight;
    }

    private static BigDecimal duplicateCoursePenalty(long duplicateDays, int totalDays) {
        if (totalDays == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((double) duplicateDays / totalDays).setScale(4, RoundingMode.HALF_UP);
    }

    private static double continuousPenaltySum(Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> teacherDayItems) {
        return teacherDayItems.values().stream()
                .flatMap(dayItems -> dayItems.values().stream())
                .mapToDouble(DeltaPenaltyScorer::continuousSamplePenalty)
                .sum();
    }

    private static int continuousSampleCount(Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> teacherDayItems) {
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

    private static double classGapPenaltySum(Map<ScoringFunctions.WeekOwner, Map<Integer, List<SchedulePlanItem>>> classDayItems) {
        return classDayItems.values().stream()
                .flatMap(dayItems -> dayItems.values().stream())
                .mapToDouble(DeltaPenaltyScorer::classGapSamplePenalty)
                .sum();
    }

    private static double classGapSamplePenalty(List<SchedulePlanItem> items) {
        if (items == null || items.isEmpty()) {
            return 0D;
        }
        return ScoringFunctions.classDayGapSamplePenalty(items.stream()
                .map(SchedulePlanItem::getStartPeriod)
                .toList());
    }

    private static double classGapSamplePenaltyAfter(List<SchedulePlanItem> beforeItems, SchedulePlanItem candidate) {
        List<Integer> starts = new ArrayList<>();
        if (beforeItems != null) {
            starts.addAll(beforeItems.stream().map(SchedulePlanItem::getStartPeriod).toList());
        }
        starts.add(candidate.getStartPeriod());
        return ScoringFunctions.classDayGapSamplePenalty(starts);
    }

    private static BigDecimal classroomPenalty(int totalItems, int roomCount, long sumSquares) {
        if (totalItems <= 0 || roomCount == 0) {
            return BigDecimal.ZERO;
        }
        double avg = (double) totalItems / roomCount;
        double variance = (double) sumSquares / roomCount - avg * avg;
        return BigDecimal.valueOf(Math.min(1D, variance / Math.max(1D, avg * avg))).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * V10 β 版：courseDayCounts 的 key 加 (weekType, weekRangeKey) 维度。
     * ALL 展开后同 item 产生 ODD/EVEN 两个 key，每个 key 再按周段签名区分。
     */
    private static String courseDayKeyBeta(SchedulePlanItem item, String weekType) {
        String rangeKey = WeekPatternSupport.weekRangeKey(item.getWeekType(), item.getStartWeek(), item.getEndWeek());
        return item.getClassId() + "_" + item.getCourseId() + "_" + item.getWeekday() + "_" + weekType + "_" + rangeKey;
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
