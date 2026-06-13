package com.paike.scheduler.engine.optimize;

import com.paike.scheduler.engine.conflict.InMemoryConflictDetector;
import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineSolution;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.engine.solver.BacktrackingSolver;
import com.paike.scheduler.engine.solver.EngineFacade;
import com.paike.scheduler.engine.solver.SolverConfig;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.service.scheduling.DeltaPenaltyScorer;
import com.paike.scheduler.service.scheduling.ScoringFunctions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnealingOptimizerTest {

    @Test
    void sameSeedSameDataProducesIdenticalAssignments() {
        EngineContext ctx = optimizationContext(List.of(), List.of());
        SolverConfig config = new SolverConfig(77L, 100_000, 5_000L, 10_000L, true);

        EngineSolution first = EngineFacade.solve(ctx, config);
        EngineSolution second = EngineFacade.solve(ctx, config);

        assertEquals(first.assignments(), second.assignments());
        assertEquals(first.stats().annealingSteps(), second.stats().annealingSteps());
    }

    @Test
    void annealingOutputDoesNotWorsenObjectiveAndKeepsHardConstraints() {
        EngineContext ctx = optimizationContext(List.of(), List.of());
        SolverConfig config = new SolverConfig(42L, 100_000, 5_000L, 10_000L, true);
        ObjectiveFunction objective = new ObjectiveFunction(ctx);

        EngineSolution initial = new BacktrackingSolver(ctx, config).solve();
        EngineSolution optimized = EngineFacade.solve(ctx, config);

        assertTrue(objective.evaluate(optimized.assignments()).totalPenalty()
                .compareTo(objective.evaluate(initial.assignments()).totalPenalty()) <= 0);
        assertTrue(new NeighborOperator(ctx).isFeasible(optimized.assignments()));
        assertTrue(optimized.stats().annealingSteps() > 0);
    }

    @Test
    void objectiveFunctionMatchesOfflineScoringPenaltySeries() {
        EngineContext ctx = optimizationContext(List.of(), List.of());
        ObjectiveFunction objective = new ObjectiveFunction(ctx);
        List<Assignment> assignments = List.of(
                new Assignment(0, 0, 0, 0),
                new Assignment(1, 0, 1, 1),
                new Assignment(2, 0, 2, 0),
                new Assignment(3, 0, 3, 1)
        );

        ObjectiveFunction.ObjectiveValue value = objective.evaluate(assignments);
        List<SchedulePlanItem> items = objective.toPlanItems(assignments);

        assertEquals(ScoringFunctions.penaltyVariance(nestedDayCounts(items, SchedulePlanItem::getClassId)),
                value.penalties().get(DeltaPenaltyScorer.CLASS_DAILY_BALANCE));
        assertEquals(ScoringFunctions.penaltyVariance(nestedDayCounts(items, SchedulePlanItem::getTeacherId)),
                value.penalties().get(DeltaPenaltyScorer.TEACHER_DAILY_LOAD));
        assertEquals(ScoringFunctions.penaltyDuplicateCourse(courseDayCounts(items)),
                value.penalties().get(DeltaPenaltyScorer.COURSE_DISTRIBUTION));
        assertEquals(ScoringFunctions.penaltyContinuous(nestedDayItems(items, SchedulePlanItem::getTeacherId)),
                value.penalties().get(DeltaPenaltyScorer.CONTINUOUS_PERIOD_LIMIT));
        assertEquals(ScoringFunctions.penaltyClassroomUtilization(roomUseCounts(items), items.size()),
                value.penalties().get(DeltaPenaltyScorer.CLASSROOM_UTILIZATION));
        assertEquals(ScoringFunctions.penaltyMorningPriority(items, ctx.afternoonStartPeriod()),
                value.penalties().get(DeltaPenaltyScorer.MORNING_THEORY_PRIORITY));
    }

    @Test
    void neighborKeepsFeasibleSolutionAndDoesNotMoveLockedBaseline() {
        Assignment locked = new Assignment(0, 0, 0, 0);
        EngineContext ctx = optimizationContext(List.of(locked), List.of());
        NeighborOperator operator = new NeighborOperator(ctx);
        List<Assignment> current = List.of(
                new Assignment(1, 0, 1, 1),
                new Assignment(2, 0, 2, 0),
                new Assignment(3, 0, 3, 1)
        );

        List<Assignment> neighbor = operator.next(current, new Random(9L)).orElseThrow();

        assertTrue(operator.isFeasible(neighbor));
        assertFalse(neighbor.contains(locked));
        assertNotEquals(current, neighbor);
    }

    @Test
    void annealingHistoryNeverReturnsHardViolation() {
        EngineContext ctx = optimizationContext(List.of(), List.of());
        SolverConfig config = new SolverConfig(12L, 100_000, 5_000L, 10_000L, true);
        EngineSolution solution = EngineFacade.solve(ctx, config);
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);

        for (Assignment assignment : solution.assignments()) {
            assertEquals(null, detector.check(assignment));
            detector.place(assignment);
        }
    }

    private static EngineContext optimizationContext(List<Assignment> locked, List<Assignment> existing) {
        List<EngineTask> tasks = List.of(
                new EngineTask(0, 101L, 0, 0, 0, 1, "NORMAL", 30, List.of(0, 1)),
                new EngineTask(1, 102L, 1, 0, 1, 1, "NORMAL", 30, List.of(0, 1)),
                new EngineTask(2, 103L, 0, 1, 0, 1, "NORMAL", 30, List.of(0, 1)),
                new EngineTask(3, 104L, 1, 1, 1, 1, "NORMAL", 30, List.of(0, 1))
        );
        return new EngineContext(
                tasks,
                List.of(
                        new EngineContext.TimeSlotData(0, 201L, 1, 1),
                        new EngineContext.TimeSlotData(1, 202L, 1, 2),
                        new EngineContext.TimeSlotData(2, 203L, 2, 1),
                        new EngineContext.TimeSlotData(3, 204L, 2, 2),
                        new EngineContext.TimeSlotData(4, 205L, 3, 1),
                        new EngineContext.TimeSlotData(5, 206L, 3, 2)
                ),
                List.of(
                        new EngineContext.ClassroomData(0, 301L, 40, "NORMAL"),
                        new EngineContext.ClassroomData(1, 302L, 40, "NORMAL")
                ),
                List.of(
                        new EngineContext.TeacherData(0, 401L, "T1", 1),
                        new EngineContext.TeacherData(1, 402L, "T2", 1)
                ),
                List.of(
                        new EngineContext.ClassData(0, 501L, 30, 1),
                        new EngineContext.ClassData(1, 502L, 30, 1)
                ),
                List.of(
                        new EngineContext.CourseData(0, 601L, "NORMAL"),
                        new EngineContext.CourseData(1, 602L, "NORMAL")
                ),
                new boolean[2][6],
                new boolean[2],
                new boolean[2],
                new boolean[2],
                4,
                4,
                true,
                5,
                Map.of(
                        DeltaPenaltyScorer.CLASS_DAILY_BALANCE, 30D,
                        DeltaPenaltyScorer.TEACHER_DAILY_LOAD, 30D,
                        DeltaPenaltyScorer.COURSE_DISTRIBUTION, 25D,
                        DeltaPenaltyScorer.CONTINUOUS_PERIOD_LIMIT, 25D,
                        DeltaPenaltyScorer.CLASSROOM_UTILIZATION, 20D,
                        DeltaPenaltyScorer.MORNING_THEORY_PRIORITY, 20D
                ),
                locked,
                existing,
                new int[tasks.size()]);
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
        Map<Long, Long> counts = items.stream()
                .map(SchedulePlanItem::getClassroomId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), id -> 0L));
        items.stream()
                .map(SchedulePlanItem::getClassroomId)
                .forEach(roomId -> counts.merge(roomId, 1L, Long::sum));
        return counts;
    }
}
