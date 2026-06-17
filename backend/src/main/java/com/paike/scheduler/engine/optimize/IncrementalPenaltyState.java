package com.paike.scheduler.engine.optimize;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.service.WeekTypeSupport;
import com.paike.scheduler.service.scheduling.DeltaPenaltyScorer;
import com.paike.scheduler.service.scheduling.ScoringFunctions;
import com.paike.scheduler.service.scheduling.ScoringFunctions.WeekOwner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R4 增量惩罚状态：持有当前 assignments 的聚合 Maps 与各维度 penalty 缓存，
 * 邻域变动时 {@link #apply} / {@link #revert} 改 Map + 重算 6 个 penalty
 * （O(map_size)，省去 evaluate 每次 groupingBy(items) 的 O(items) 成本）。
 *
 * <p><b>行为硬保证</b>：{@code value()} 与
 * {@code objectiveFunction.evaluate(currentList)} 字节级一致（同
 * {@code ScoringFunctions.penaltyXxx} 调用同 scale 舍入）。由
 * {@code IncrementalPenaltyStateTest} 对拍验证，AnnealingOptimizer
 * 改用增量路径后同 seed 同数据必产同一 best（accept/reject 序列不变）。</p>
 *
 * <p>状态不持有 assignments 列表（持有者是 AnnealingOptimizer），apply/revert
 * 必须严格配对：accept 后 state 已反映新值；reject 后调用 revert 反向回滚。</p>
 */
final class IncrementalPenaltyState {

    private final EngineContext ctx;
    private final ObjectiveFunction objectiveFunction;
    private final int afternoonStartPeriod;
    private final int currentSize;

    // V9 阶段3B β：owner 维度加 weekType（ALL 展开成 ODD+EVEN），roomUseCounts/afternoonCount 不变
    private final Map<WeekOwner, Map<Integer, Long>> classDayCounts;
    private final Map<WeekOwner, Map<Integer, Long>> teacherDayCounts;
    private final Map<String, Long> courseDayCounts;
    private final Map<WeekOwner, Map<Integer, List<Integer>>> teacherDayStarts;
    private final Map<WeekOwner, Map<Integer, List<Integer>>> classDayStarts;
    private final Map<Long, Long> roomUseCounts;
    private long afternoonCount;

    private BigDecimal penaltyClassBalance;
    private BigDecimal penaltyTeacherLoad;
    private BigDecimal penaltyCourseDist;
    private BigDecimal penaltyContinuous;
    private BigDecimal penaltyClassroomUtil;
    private BigDecimal penaltyMorning;
    private BigDecimal penaltyClassGap;
    private BigDecimal totalPenalty;

    private IncrementalPenaltyState(
            EngineContext ctx,
            ObjectiveFunction objectiveFunction,
            int afternoonStartPeriod,
            int currentSize,
            Map<WeekOwner, Map<Integer, Long>> classDayCounts,
            Map<WeekOwner, Map<Integer, Long>> teacherDayCounts,
            Map<String, Long> courseDayCounts,
            Map<WeekOwner, Map<Integer, List<Integer>>> teacherDayStarts,
            Map<WeekOwner, Map<Integer, List<Integer>>> classDayStarts,
            Map<Long, Long> roomUseCounts,
            long afternoonCount,
            BigDecimal penaltyClassBalance,
            BigDecimal penaltyTeacherLoad,
            BigDecimal penaltyCourseDist,
            BigDecimal penaltyContinuous,
            BigDecimal penaltyClassroomUtil,
            BigDecimal penaltyMorning,
            BigDecimal penaltyClassGap,
            BigDecimal totalPenalty
    ) {
        this.ctx = ctx;
        this.objectiveFunction = objectiveFunction;
        this.afternoonStartPeriod = afternoonStartPeriod;
        this.currentSize = currentSize;
        this.classDayCounts = classDayCounts;
        this.teacherDayCounts = teacherDayCounts;
        this.courseDayCounts = courseDayCounts;
        this.teacherDayStarts = teacherDayStarts;
        this.classDayStarts = classDayStarts;
        this.roomUseCounts = roomUseCounts;
        this.afternoonCount = afternoonCount;
        this.penaltyClassBalance = penaltyClassBalance;
        this.penaltyTeacherLoad = penaltyTeacherLoad;
        this.penaltyCourseDist = penaltyCourseDist;
        this.penaltyContinuous = penaltyContinuous;
        this.penaltyClassroomUtil = penaltyClassroomUtil;
        this.penaltyMorning = penaltyMorning;
        this.penaltyClassGap = penaltyClassGap;
        this.totalPenalty = totalPenalty;
    }

    static IncrementalPenaltyState from(List<Assignment> initial, ObjectiveFunction fn, EngineContext ctx) {
        List<SchedulePlanItem> items = fn.toPlanItems(initial);
        Map<WeekOwner, Map<Integer, Long>> classDayCounts = nestedDayCountsBeta(items, SchedulePlanItem::getClassId);
        Map<WeekOwner, Map<Integer, Long>> teacherDayCounts = nestedDayCountsBeta(items, SchedulePlanItem::getTeacherId);
        Map<String, Long> courseDayCounts = courseDayCountsBeta(items);
        Map<WeekOwner, Map<Integer, List<Integer>>> teacherDayStarts = teacherDayStartsBeta(items);
        Map<WeekOwner, Map<Integer, List<Integer>>> classDayStarts = classDayStartsBeta(items);
        Map<Long, Long> roomUseCounts = activeRoomUseCounts(ctx, items);
        long afternoonCount = items.stream()
                .filter(it -> it.getStartPeriod() >= ctx.afternoonStartPeriod())
                .count();

        BigDecimal pcb = ScoringFunctions.penaltyVarianceBeta(classDayCounts);
        BigDecimal ptl = ScoringFunctions.penaltyVarianceBeta(teacherDayCounts);
        BigDecimal pcd = ScoringFunctions.penaltyDuplicateCourse(courseDayCounts);
        BigDecimal pcn = ScoringFunctions.penaltyContinuousBeta(teacherDayStartsAsItemsBeta(items));
        BigDecimal pcu = ScoringFunctions.penaltyClassroomUtilization(roomUseCounts, items.size());
        BigDecimal pmn = BigDecimal.valueOf(items.isEmpty() ? 0D : (double) afternoonCount / items.size())
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal pcg = ScoringFunctions.penaltyClassGapBeta(classDayStartsAsItemsBeta(items));
        BigDecimal total = weightedTotal(fn, pcb, ptl, pcd, pcn, pcu, pmn, pcg);

        return new IncrementalPenaltyState(
                ctx, fn, ctx.afternoonStartPeriod(),
                initial.size(),
                classDayCounts, teacherDayCounts, courseDayCounts,
                teacherDayStarts, classDayStarts, roomUseCounts, afternoonCount,
                pcb, ptl, pcd, pcn, pcu, pmn, pcg, total);
    }

    int currentSize() {
        return currentSize;
    }

    BigDecimal totalPenalty() {
        return totalPenalty;
    }

    ObjectiveFunction.ObjectiveValue value() {
        Map<String, BigDecimal> penalties = new LinkedHashMap<>();
        penalties.put(DeltaPenaltyScorer.CLASS_DAILY_BALANCE, penaltyClassBalance);
        penalties.put(DeltaPenaltyScorer.TEACHER_DAILY_LOAD, penaltyTeacherLoad);
        penalties.put(DeltaPenaltyScorer.COURSE_DISTRIBUTION, penaltyCourseDist);
        penalties.put(DeltaPenaltyScorer.CONTINUOUS_PERIOD_LIMIT, penaltyContinuous);
        penalties.put(DeltaPenaltyScorer.CLASSROOM_UTILIZATION, penaltyClassroomUtil);
        penalties.put(DeltaPenaltyScorer.MORNING_THEORY_PRIORITY, penaltyMorning);
        penalties.put(DeltaPenaltyScorer.CLASS_GAP_PENALTY, penaltyClassGap);
        return new ObjectiveFunction.ObjectiveValue(totalPenalty, score(totalPenalty), penalties);
    }

    /**
     * 应用邻域（接受路径）：从 Maps 移除 {@code removed}，加入 {@code added}，重算 6 个 penalty 缓存。
     * 列表持有者（AnnealingOptimizer）须同步 {@code current.set(idx, added)}。
     */
    void apply(Assignment removed, Assignment added) {
        SchedulePlanItem removedItem = toItem(removed);
        SchedulePlanItem addedItem = toItem(added);

        decrementAggregates(removedItem);
        incrementAggregates(addedItem);
        if (addedItem.getStartPeriod() >= afternoonStartPeriod) {
            afternoonCount++;
        }
        if (removedItem.getStartPeriod() >= afternoonStartPeriod) {
            afternoonCount--;
        }
        recomputePenalties();
    }

    /**
     * 回滚邻域（拒绝路径）：与 {@link #apply} 完全反向，必须在 apply 之后立即调用。
     * {@code added} 必须在 apply 之后状态里，{@code removed} 是要恢复的。
     */
    void revert(Assignment added, Assignment removed) {
        // 严格反向: 把 added 从 Maps 减掉, 把 removed 加回, afternoonCount 复原。
        SchedulePlanItem addedItem = toItem(added);
        SchedulePlanItem removedItem = toItem(removed);

        decrementAggregates(addedItem);
        incrementAggregates(removedItem);
        if (addedItem.getStartPeriod() >= afternoonStartPeriod) {
            afternoonCount--;
        }
        if (removedItem.getStartPeriod() >= afternoonStartPeriod) {
            afternoonCount++;
        }
        recomputePenalties();
    }

    // ---------- 内部 ----------

    private void recomputePenalties() {
        penaltyClassBalance = ScoringFunctions.penaltyVarianceBeta(classDayCounts);
        penaltyTeacherLoad = ScoringFunctions.penaltyVarianceBeta(teacherDayCounts);
        penaltyCourseDist = ScoringFunctions.penaltyDuplicateCourse(courseDayCounts);
        penaltyContinuous = ScoringFunctions.penaltyContinuousBeta(teacherDayStartsAsItemsFromCurrentMap());
        penaltyClassroomUtil = ScoringFunctions.penaltyClassroomUtilization(roomUseCounts, currentSize);
        penaltyMorning = BigDecimal.valueOf(currentSize == 0 ? 0D : (double) afternoonCount / currentSize)
                .setScale(4, RoundingMode.HALF_UP);
        penaltyClassGap = ScoringFunctions.penaltyClassGapBeta(classDayStartsAsItemsFromCurrentMap());
        totalPenalty = weightedTotal(objectiveFunction, penaltyClassBalance, penaltyTeacherLoad,
                penaltyCourseDist, penaltyContinuous, penaltyClassroomUtil, penaltyMorning, penaltyClassGap);
    }

    private SchedulePlanItem toItem(Assignment a) {
        return ObjectiveFunction.assignmentToItem(ctx, a);
    }

    /**
     * V9 阶段3B β：按 countableWeekTypes 展开到对应周次桶（ALL→ODD+EVEN 两个）。
     * teacherDayStarts 的移除后清空逻辑保持（避免空 list 拉低 continuous 均值）。
     */
    private void decrementAggregates(SchedulePlanItem it) {
        for (String wt : WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
            decrementNestedCount(classDayCounts, new WeekOwner(it.getClassId(), wt), it.getWeekday());
            decrementNestedCount(teacherDayCounts, new WeekOwner(it.getTeacherId(), wt), it.getWeekday());
            decrementFlatCount(courseDayCounts, it.getClassId() + "_" + it.getCourseId() + "_" + it.getWeekday() + "_" + wt);
            WeekOwner teacherKey = new WeekOwner(it.getTeacherId(), wt);
            Map<Integer, List<Integer>> teacherDays = teacherDayStarts.get(teacherKey);
            if (teacherDays != null) {
                List<Integer> starts = teacherDays.get(it.getWeekday());
                if (starts != null) {
                    starts.remove(Integer.valueOf(it.getStartPeriod()));
                    if (starts.isEmpty()) {
                        teacherDays.remove(it.getWeekday());
                        if (teacherDays.isEmpty()) {
                            teacherDayStarts.remove(teacherKey);
                        }
                    }
                }
            }
            WeekOwner classKey = new WeekOwner(it.getClassId(), wt);
            Map<Integer, List<Integer>> classDays = classDayStarts.get(classKey);
            if (classDays != null) {
                List<Integer> starts = classDays.get(it.getWeekday());
                if (starts != null) {
                    starts.remove(Integer.valueOf(it.getStartPeriod()));
                    if (starts.isEmpty()) {
                        classDays.remove(it.getWeekday());
                        if (classDays.isEmpty()) {
                            classDayStarts.remove(classKey);
                        }
                    }
                }
            }
        }
        decrementRoomCount(it.getClassroomId());
    }

    private void incrementAggregates(SchedulePlanItem it) {
        for (String wt : WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
            incrementNestedCount(classDayCounts, new WeekOwner(it.getClassId(), wt), it.getWeekday());
            incrementNestedCount(teacherDayCounts, new WeekOwner(it.getTeacherId(), wt), it.getWeekday());
            incrementFlatCount(courseDayCounts, it.getClassId() + "_" + it.getCourseId() + "_" + it.getWeekday() + "_" + wt);
            teacherDayStarts.computeIfAbsent(new WeekOwner(it.getTeacherId(), wt), k -> new HashMap<>())
                    .computeIfAbsent(it.getWeekday(), k -> new ArrayList<>())
                    .add(it.getStartPeriod());
            classDayStarts.computeIfAbsent(new WeekOwner(it.getClassId(), wt), k -> new HashMap<>())
                    .computeIfAbsent(it.getWeekday(), k -> new ArrayList<>())
                    .add(it.getStartPeriod());
        }
        incrementRoomCount(it.getClassroomId());
    }

    private static <K> void decrementFlatCount(Map<K, Long> map, K key) {
        Long current = map.get(key);
        if (current == null) {
            return;
        }
        if (current <= 1L) {
            map.remove(key);
        } else {
            map.put(key, current - 1L);
        }
    }

    private static <K> void incrementFlatCount(Map<K, Long> map, K key) {
        map.merge(key, 1L, Long::sum);
    }

    private static <K1, K2> void decrementNestedCount(Map<K1, Map<K2, Long>> map, K1 k1, K2 k2) {
        Map<K2, Long> inner = map.get(k1);
        if (inner == null) {
            return;
        }
        Long current = inner.get(k2);
        if (current == null) {
            return;
        }
        if (current <= 1L) {
            inner.remove(k2);
            if (inner.isEmpty()) {
                map.remove(k1);
            }
        } else {
            inner.put(k2, current - 1L);
        }
    }

    private static <K1, K2> void incrementNestedCount(Map<K1, Map<K2, Long>> map, K1 k1, K2 k2) {
        map.computeIfAbsent(k1, k -> new HashMap<>()).merge(k2, 1L, Long::sum);
    }

    /**
     * 与全量路径对齐：永远保留所有 active 教室作为 0 占位 key（{@code penaltyClassroomUtilization}
     * 的 avg = totalItems / size 与 avg² 依赖 size，未使用教室缺失会偏大 penalty 偏小）。
     * 故 0 不删 key，仅 put 0。
     */
    private void decrementRoomCount(Long roomId) {
        if (roomId == null) {
            return;
        }
        Long current = roomUseCounts.get(roomId);
        if (current == null) {
            roomUseCounts.put(roomId, 0L);
            return;
        }
        if (current <= 1L) {
            roomUseCounts.put(roomId, 0L);
        } else {
            roomUseCounts.put(roomId, current - 1L);
        }
    }

    private void incrementRoomCount(Long roomId) {
        if (roomId == null) {
            return;
        }
        roomUseCounts.merge(roomId, 1L, Long::sum);
    }

    /**
     * penaltyContinuous 期望 {@code Map<teacher, Map<day, List<SchedulePlanItem>>}}。
     * 此处从增量维护的 teacherDayStarts(startPeriod 列表) 重建 items 列表。
     * 每次 apply/revert 后都重建（O(unique_teacher_day) ≈ O(teachers × days) ≈ O(400)），
     * 与全量路径遍历 items 行为对齐。
     */
    /** β 版：outer key 从 Long 改 WeekOwner，供 penaltyContinuousBeta 使用 */
    private Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> teacherDayStartsAsItemsFromCurrentMap() {
        Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> result = new HashMap<>();
        for (Map.Entry<WeekOwner, Map<Integer, List<Integer>>> teacherEntry : teacherDayStarts.entrySet()) {
            WeekOwner teacherKey = teacherEntry.getKey();
            for (Map.Entry<Integer, List<Integer>> dayEntry : teacherEntry.getValue().entrySet()) {
                Integer day = dayEntry.getKey();
                List<SchedulePlanItem> items = new ArrayList<>();
                for (Integer start : dayEntry.getValue()) {
                    items.add(buildSyntheticItem(teacherKey.ownerId(), day, start));
                }
                result.computeIfAbsent(teacherKey, k -> new HashMap<>()).put(day, items);
            }
        }
        return result;
    }

    private SchedulePlanItem buildSyntheticItem(Long teacherId, Integer weekday, Integer startPeriod) {
        SchedulePlanItem it = new SchedulePlanItem();
        it.setTeacherId(teacherId);
        it.setWeekday(weekday);
        it.setStartPeriod(startPeriod);
        it.setEndPeriod(startPeriod + 1);
        return it;
    }

    // ---------- 一次性构造（from 时用，β 版） ----------

    /** β 版：owner 维度加 weekType，ALL 展开成 ODD+EVEN 两个独立子桶 */
    private static Map<WeekOwner, Map<Integer, Long>> nestedDayCountsBeta(
            List<SchedulePlanItem> items,
            java.util.function.Function<SchedulePlanItem, Long> ownerExtractor) {
        Map<WeekOwner, Map<Integer, Long>> result = new HashMap<>();
        for (SchedulePlanItem it : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
                result.computeIfAbsent(new WeekOwner(ownerExtractor.apply(it), wt), k -> new HashMap<>())
                        .merge(it.getWeekday(), 1L, Long::sum);
            }
        }
        return result;
    }

    private static Map<String, Long> courseDayCountsBeta(List<SchedulePlanItem> items) {
        Map<String, Long> result = new HashMap<>();
        for (SchedulePlanItem it : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
                result.merge(it.getClassId() + "_" + it.getCourseId() + "_" + it.getWeekday() + "_" + wt, 1L, Long::sum);
            }
        }
        return result;
    }

    private static Map<WeekOwner, Map<Integer, List<Integer>>> teacherDayStartsBeta(List<SchedulePlanItem> items) {
        Map<WeekOwner, Map<Integer, List<Integer>>> result = new HashMap<>();
        for (SchedulePlanItem it : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
                result.computeIfAbsent(new WeekOwner(it.getTeacherId(), wt), k -> new HashMap<>())
                        .computeIfAbsent(it.getWeekday(), k -> new ArrayList<>())
                        .add(it.getStartPeriod());
            }
        }
        return result;
    }

    private static Map<Long, Long> activeRoomUseCounts(EngineContext ctx, List<SchedulePlanItem> items) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (EngineContext.ClassroomData room : ctx.classrooms()) {
            if (!ctx.classroomDisabled()[room.index()]) {
                counts.put(room.originalId(), 0L);
            }
        }
        for (SchedulePlanItem it : items) {
            if (it.getClassroomId() != null) {
                counts.merge(it.getClassroomId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    /** β 版：连续上课限制按 (teacher × weekType × day) 分桶 */
    private static Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> teacherDayStartsAsItemsBeta(List<SchedulePlanItem> items) {
        Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> result = new HashMap<>();
        for (SchedulePlanItem it : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
                result.computeIfAbsent(new WeekOwner(it.getTeacherId(), wt), k -> new HashMap<>())
                        .computeIfAbsent(it.getWeekday(), k -> new ArrayList<>())
                        .add(it);
            }
        }
        return result;
    }

    /** β 版：班级空堂按 (class × weekType × day) 分桶，仅存 startPeriod（from 时一次性构造）。 */
    private static Map<WeekOwner, Map<Integer, List<Integer>>> classDayStartsBeta(List<SchedulePlanItem> items) {
        Map<WeekOwner, Map<Integer, List<Integer>>> result = new HashMap<>();
        for (SchedulePlanItem it : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
                result.computeIfAbsent(new WeekOwner(it.getClassId(), wt), k -> new HashMap<>())
                        .computeIfAbsent(it.getWeekday(), k -> new ArrayList<>())
                        .add(it.getStartPeriod());
            }
        }
        return result;
    }

    /** β 版：班级空堂按 (class × weekType × day) 分桶到 items（from 时给 penaltyClassGapBeta 用）。 */
    private static Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> classDayStartsAsItemsBeta(List<SchedulePlanItem> items) {
        Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> result = new HashMap<>();
        for (SchedulePlanItem it : items) {
            for (String wt : WeekTypeSupport.countableWeekTypes(it.getWeekType())) {
                result.computeIfAbsent(new WeekOwner(it.getClassId(), wt), k -> new HashMap<>())
                        .computeIfAbsent(it.getWeekday(), k -> new ArrayList<>())
                        .add(it);
            }
        }
        return result;
    }

    /** 从增量维护的 classDayStarts 重建 items（每次 apply/revert 后，供 penaltyClassGapBeta）。 */
    private Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> classDayStartsAsItemsFromCurrentMap() {
        Map<WeekOwner, Map<Integer, List<SchedulePlanItem>>> result = new HashMap<>();
        for (Map.Entry<WeekOwner, Map<Integer, List<Integer>>> classEntry : classDayStarts.entrySet()) {
            WeekOwner classKey = classEntry.getKey();
            for (Map.Entry<Integer, List<Integer>> dayEntry : classEntry.getValue().entrySet()) {
                Integer day = dayEntry.getKey();
                List<SchedulePlanItem> items = new ArrayList<>();
                for (Integer start : dayEntry.getValue()) {
                    items.add(buildSyntheticItem(classKey.ownerId(), day, start));
                }
                result.computeIfAbsent(classKey, k -> new HashMap<>()).put(day, items);
            }
        }
        return result;
    }

    private static BigDecimal weightedTotal(
            ObjectiveFunction fn,
            BigDecimal pcb, BigDecimal ptl, BigDecimal pcd,
            BigDecimal pcn, BigDecimal pcu, BigDecimal pmn, BigDecimal pcg) {
        BigDecimal sum = BigDecimal.ZERO;
        for (String code : DeltaPenaltyScorer.SOFT_RULE_CODES) {
            BigDecimal w = fn.weightFor(code);
            BigDecimal p = switch (code) {
                case DeltaPenaltyScorer.CLASS_DAILY_BALANCE -> pcb;
                case DeltaPenaltyScorer.TEACHER_DAILY_LOAD -> ptl;
                case DeltaPenaltyScorer.COURSE_DISTRIBUTION -> pcd;
                case DeltaPenaltyScorer.CONTINUOUS_PERIOD_LIMIT -> pcn;
                case DeltaPenaltyScorer.CLASSROOM_UTILIZATION -> pcu;
                case DeltaPenaltyScorer.MORNING_THEORY_PRIORITY -> pmn;
                case DeltaPenaltyScorer.CLASS_GAP_PENALTY -> pcg;
                default -> BigDecimal.ZERO;
            };
            sum = sum.add(w.multiply(p));
        }
        return sum.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal score(BigDecimal totalPenalty) {
        return new BigDecimal("100.0000").subtract(totalPenalty).setScale(4, RoundingMode.HALF_UP);
    }
}
