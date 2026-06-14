package com.paike.scheduler.engine.optimize;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.service.WeekTypeSupport;
import com.paike.scheduler.service.scheduling.DeltaPenaltyScorer;
import com.paike.scheduler.service.scheduling.ScoringFunctions;
import com.paike.scheduler.service.scheduling.ScoringFunctions.WeekOwner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * R4 objective: offline penalty series multiplied by enabled SOFT weights.
 */
public final class ObjectiveFunction {

    private static final BigDecimal FULL_SCORE = new BigDecimal("100.0000");

    private final EngineContext ctx;
    private final Collection<Long> activeClassroomIds;

    public ObjectiveFunction(EngineContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx must not be null");
        this.activeClassroomIds = ctx.classrooms().stream()
                .filter(room -> !ctx.classroomDisabled()[room.index()])
                .map(EngineContext.ClassroomData::originalId)
                .toList();
    }

    public ObjectiveValue evaluate(List<Assignment> assignments) {
        List<SchedulePlanItem> items = toPlanItems(assignments);
        Map<String, BigDecimal> penalties = penalties(items);
        BigDecimal totalPenalty = BigDecimal.ZERO;
        for (String ruleCode : DeltaPenaltyScorer.SOFT_RULE_CODES) {
            BigDecimal weight = weight(ruleCode);
            BigDecimal penalty = penalties.getOrDefault(ruleCode, BigDecimal.ZERO);
            totalPenalty = totalPenalty.add(weight.multiply(penalty));
        }
        totalPenalty = totalPenalty.setScale(4, RoundingMode.HALF_UP);
        BigDecimal score = FULL_SCORE.subtract(totalPenalty).setScale(4, RoundingMode.HALF_UP);
        return new ObjectiveValue(totalPenalty, score, penalties);
    }

    public List<SchedulePlanItem> toPlanItems(List<Assignment> assignments) {
        return assignments.stream()
                .map(this::toPlanItem)
                .toList();
    }

    /** 包内供 IncrementalPenaltyState 复用：从单个 Assignment 构 SchedulePlanItem（与 toPlanItems 一致）。 */
    static SchedulePlanItem assignmentToItem(EngineContext ctx, Assignment assignment) {
        EngineTask task = ctx.tasks().get(assignment.taskIndex());
        EngineContext.TimeSlotData slot = ctx.timeSlots().get(assignment.timeSlotIndex());
        EngineContext.ClassroomData room = ctx.classrooms().get(assignment.classroomIndex());

        SchedulePlanItem item = new SchedulePlanItem();
        item.setTeachingTaskId(task.originalId());
        item.setTeacherId(ctx.teachers().get((int) task.teacherIndex()).originalId());
        item.setClassId(ctx.classes().get((int) task.classIndex()).originalId());
        item.setCourseId(ctx.courses().get((int) task.courseIndex()).originalId());
        item.setClassroomId(room.originalId());
        item.setWeekday(slot.dayOfWeek());
        item.setStartPeriod(slotToStartPeriod(slot));
        item.setEndPeriod(slotToStartPeriod(slot) + 1);
        item.setWeekType(task.weekType());
        item.setConflictFlag(0);
        item.setSourceType("AUTO");
        return item;
    }

    /** 包内供 IncrementalPenaltyState 复用：从 ctx 取规则权重（与 private weight 同一映射）。 */
    BigDecimal weightFor(String ruleCode) {
        return weight(ruleCode);
    }

    private SchedulePlanItem toPlanItem(Assignment assignment) {
        EngineTask task = ctx.tasks().get(assignment.taskIndex());
        EngineContext.TimeSlotData slot = ctx.timeSlots().get(assignment.timeSlotIndex());
        EngineContext.ClassroomData room = ctx.classrooms().get(assignment.classroomIndex());

        SchedulePlanItem item = new SchedulePlanItem();
        item.setTeachingTaskId(task.originalId());
        item.setTeacherId(ctx.teachers().get((int) task.teacherIndex()).originalId());
        item.setClassId(ctx.classes().get((int) task.classIndex()).originalId());
        item.setCourseId(ctx.courses().get((int) task.courseIndex()).originalId());
        item.setClassroomId(room.originalId());
        item.setWeekday(slot.dayOfWeek());
        item.setStartPeriod(slotToStartPeriod(slot));
        item.setEndPeriod(slotToStartPeriod(slot) + 1);
        item.setWeekType(task.weekType());
        item.setConflictFlag(0);
        item.setSourceType("AUTO");
        return item;
    }

    /**
     * V9 阶段3B β 激活：聚合按 (owner, weekType) 分桶，ALL 展开成 ODD+EVEN。
     * 与 ScheduleScoreService / DeltaPenaltyScorer 同源（保证在线/离线/引擎三方一致）。
     */
    private Map<String, BigDecimal> penalties(List<SchedulePlanItem> items) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        result.put(DeltaPenaltyScorer.CLASS_DAILY_BALANCE,
                ScoringFunctions.penaltyVarianceBeta(nestedDayCountsBeta(items, SchedulePlanItem::getClassId)));
        result.put(DeltaPenaltyScorer.TEACHER_DAILY_LOAD,
                ScoringFunctions.penaltyVarianceBeta(nestedDayCountsBeta(items, SchedulePlanItem::getTeacherId)));
        result.put(DeltaPenaltyScorer.COURSE_DISTRIBUTION,
                ScoringFunctions.penaltyDuplicateCourse(courseDayCountsBeta(items)));
        result.put(DeltaPenaltyScorer.CONTINUOUS_PERIOD_LIMIT,
                ScoringFunctions.penaltyContinuousBeta(nestedDayItemsBeta(items, SchedulePlanItem::getTeacherId)));
        result.put(DeltaPenaltyScorer.CLASSROOM_UTILIZATION,
                ScoringFunctions.penaltyClassroomUtilization(roomUseCounts(items), items.size()));
        result.put(DeltaPenaltyScorer.MORNING_THEORY_PRIORITY,
                ScoringFunctions.penaltyMorningPriority(items, ctx.afternoonStartPeriod()));
        return result;
    }

    private BigDecimal weight(String ruleCode) {
        Double value = ctx.ruleWeights().get(ruleCode);
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    /** β 版：owner 维度加 weekType，ALL 展开成 ODD+EVEN 两个独立子桶 */
    private static Map<WeekOwner, Map<Integer, Long>> nestedDayCountsBeta(
            List<SchedulePlanItem> items,
            Function<SchedulePlanItem, Long> ownerExtractor
    ) {
        return items.stream()
                .flatMap(item -> WeekTypeSupport.countableWeekTypes(item.getWeekType()).stream()
                        .map(wt -> new AbstractMap.SimpleEntry<>(
                                new WeekOwner(ownerExtractor.apply(item), wt),
                                item.getWeekday())))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.groupingBy(
                                Map.Entry::getValue,
                                Collectors.counting())));
    }

    /** β 版：courseDayCounts key 加 weekType 维度（ALL 展开后同 item 产生 ODD/EVEN 两个 key） */
    private static Map<String, Long> courseDayCountsBeta(List<SchedulePlanItem> items) {
        return items.stream()
                .flatMap(item -> WeekTypeSupport.countableWeekTypes(item.getWeekType()).stream()
                        .map(wt -> new AbstractMap.SimpleEntry<>(
                                item.getClassId() + "_" + item.getCourseId() + "_" + item.getWeekday() + "_" + wt,
                                1L)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.summingLong(Map.Entry::getValue)));
    }

    /** β 版：连续上课限制按 (teacher × weekType × day) 分桶 */
    private static Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> nestedDayItemsBeta(
            List<SchedulePlanItem> items,
            Function<SchedulePlanItem, Long> ownerExtractor
    ) {
        Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> result = new HashMap<>();
        for (SchedulePlanItem item : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(item.getWeekType())) {
                WeekOwner key = new WeekOwner(ownerExtractor.apply(item), wt);
                result.computeIfAbsent(key, k -> new HashMap<>())
                        .computeIfAbsent(item.getWeekday(), d -> new ArrayList<>())
                        .add(item);
            }
        }
        return result;
    }

    private Map<Long, Long> roomUseCounts(List<SchedulePlanItem> items) {
        Map<Long, Long> counts = activeClassroomIds.stream()
                .distinct()
                .collect(Collectors.toMap(Function.identity(), id -> 0L, Long::sum, LinkedHashMap::new));
        items.stream()
                .map(SchedulePlanItem::getClassroomId)
                .filter(Objects::nonNull)
                .forEach(roomId -> counts.merge(roomId, 1L, Long::sum));
        return counts;
    }

    private static int slotToStartPeriod(EngineContext.TimeSlotData slot) {
        return switch (slot.periodNo()) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            case 4 -> 7;
            default -> Math.max(1, slot.periodNo() * 2 - 1);
        };
    }

    public record ObjectiveValue(
            BigDecimal totalPenalty,
            BigDecimal score,
            Map<String, BigDecimal> penalties
    ) {
    }
}
