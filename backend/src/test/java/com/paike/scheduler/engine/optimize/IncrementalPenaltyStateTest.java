package com.paike.scheduler.engine.optimize;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineSolution;
import com.paike.scheduler.engine.solver.BacktrackingSolver;
import com.paike.scheduler.engine.solver.SolverConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IncrementalPenaltyState 行为对拍：state.value() 必须与 ObjectiveFunction.evaluate(externalList)
 * 字节级一致（同 ScoringFunctions.penaltyXxx 调用同 scale 舍入）。
 * 这是 AnnealingOptimizer 改用增量路径后行为不变（同 seed 同数据 → 同一 best）的硬保证。
 *
 * <p>state 不持有 assignments 列表；测试用外部 list 同步维护，模拟 AnnealingOptimizer 的责任划分。</p>
 */
class IncrementalPenaltyStateTest {

    @Test
    void initialStateMatchesFullEvaluation() {
        EngineContext ctx = AnnealingOptimizerTest.optimizationContext(List.of(), List.of());
        SolverConfig config = new SolverConfig(7L, 100_000, 5_000L, true);
        EngineSolution feasible = new BacktrackingSolver(ctx, config).solve();
        ObjectiveFunction fn = new ObjectiveFunction(ctx);

        List<Assignment> current = new ArrayList<>(feasible.assignments());
        IncrementalPenaltyState state = IncrementalPenaltyState.from(current, fn, ctx);

        assertPenaltyBytesEqual(fn.evaluate(current), state.value(), "initial");
    }

    @Test
    void incrementalStateTracksFullEvaluationAcrossRandomNeighbors() {
        EngineContext ctx = AnnealingOptimizerTest.optimizationContext(List.of(), List.of());
        SolverConfig config = new SolverConfig(7L, 100_000, 5_000L, true);
        EngineSolution feasible = new BacktrackingSolver(ctx, config).solve();
        ObjectiveFunction fn = new ObjectiveFunction(ctx);
        NeighborOperator neighbor = new NeighborOperator(ctx);

        List<Assignment> current = new ArrayList<>(feasible.assignments());
        IncrementalPenaltyState state = IncrementalPenaltyState.from(current, fn, ctx);
        Random random = new Random(20260613L);

        for (int step = 0; step < 200; step++) {
            Optional<List<Assignment>> maybeNext = neighbor.next(current, random);
            if (maybeNext.isEmpty()) {
                continue;
            }
            int changedIndex = findChangedIndex(current, maybeNext.get());
            if (changedIndex < 0) {
                continue;
            }
            Assignment removed = current.get(changedIndex);
            Assignment added = maybeNext.get().get(changedIndex);
            state.apply(removed, added);
            // 同步外部 list（接受路径模拟）
            current.set(changedIndex, added);
            assertPenaltyBytesEqual(fn.evaluate(current), state.value(), "step " + step);
        }
    }

    @Test
    void applyRevertRoundTripRestoresExactValue() {
        // 拒绝路径对拍: apply(removed, added) 后 revert(added, removed) 必须把 state 恢复为原 value。
        EngineContext ctx = AnnealingOptimizerTest.optimizationContext(List.of(), List.of());
        SolverConfig config = new SolverConfig(7L, 100_000, 5_000L, true);
        EngineSolution feasible = new BacktrackingSolver(ctx, config).solve();
        ObjectiveFunction fn = new ObjectiveFunction(ctx);
        NeighborOperator neighbor = new NeighborOperator(ctx);

        List<Assignment> current = new ArrayList<>(feasible.assignments());
        IncrementalPenaltyState state = IncrementalPenaltyState.from(current, fn, ctx);
        ObjectiveFunction.ObjectiveValue before = state.value();
        Random random = new Random(99L);

        for (int trial = 0; trial < 100; trial++) {
            Optional<List<Assignment>> maybeNext = neighbor.next(current, random);
            if (maybeNext.isEmpty()) {
                continue;
            }
            int changedIndex = findChangedIndex(current, maybeNext.get());
            if (changedIndex < 0) {
                continue;
            }
            Assignment removed = current.get(changedIndex);
            Assignment added = maybeNext.get().get(changedIndex);
            state.apply(removed, added);
            state.revert(added, removed);
            assertPenaltyBytesEqual(before, state.value(), "trial " + trial + " roundtrip");
        }
    }

    @Test
    void applyIsByteIdenticalToFullEvaluationOverMultipleSeeds() {
        // 多 seed 跑: 任何 apply 序列下 state.value() 与 evaluate(externalCurrentList) 字节级一致。
        EngineContext ctx = AnnealingOptimizerTest.optimizationContext(List.of(), List.of());
        SolverConfig config = new SolverConfig(7L, 100_000, 5_000L, true);
        EngineSolution feasible = new BacktrackingSolver(ctx, config).solve();
        ObjectiveFunction fn = new ObjectiveFunction(ctx);
        NeighborOperator neighbor = new NeighborOperator(ctx);

        for (long seed : new long[]{1L, 42L, 99L, 20260613L, 7777L}) {
            List<Assignment> current = new ArrayList<>(feasible.assignments());
            IncrementalPenaltyState state = IncrementalPenaltyState.from(current, fn, ctx);
            Random random = new Random(seed);
            for (int step = 0; step < 200; step++) {
                Optional<List<Assignment>> maybeNext = neighbor.next(current, random);
                if (maybeNext.isEmpty()) {
                    continue;
                }
                int idx = findChangedIndex(current, maybeNext.get());
                if (idx < 0) {
                    continue;
                }
                Assignment removed = current.get(idx);
                Assignment added = maybeNext.get().get(idx);
                state.apply(removed, added);
                current.set(idx, added);
                ObjectiveFunction.ObjectiveValue expected = fn.evaluate(current);
                ObjectiveFunction.ObjectiveValue actual = state.value();
                if (!expected.totalPenalty().equals(actual.totalPenalty())) {
                    System.err.println("DEBUG seed=" + seed + " step=" + step);
                    System.err.println("expected.penalties=" + expected.penalties());
                    System.err.println("actual.penalties=" + actual.penalties());
                    System.err.println("current size=" + current.size());
                }
                assertPenaltyBytesEqual(expected, actual,
                        "seed=" + seed + " step=" + step);
            }
        }
    }

    private static int findChangedIndex(List<Assignment> current, List<Assignment> next) {
        if (current.size() != next.size()) {
            return -1;
        }
        List<Assignment> nextCopy = new ArrayList<>(next);
        for (int i = 0; i < current.size(); i++) {
            Assignment c = current.get(i);
            int foundAt = -1;
            for (int j = 0; j < nextCopy.size(); j++) {
                if (nextCopy.get(j) != null && nextCopy.get(j).equals(c)) {
                    foundAt = j;
                    break;
                }
            }
            if (foundAt < 0) {
                return i;
            }
            nextCopy.set(foundAt, null);
        }
        return -1;
    }

    private static void assertPenaltyBytesEqual(ObjectiveFunction.ObjectiveValue expected,
                                                 ObjectiveFunction.ObjectiveValue actual,
                                                 String label) {
        assertEquals(expected.totalPenalty(), actual.totalPenalty(), label + ": totalPenalty");
        assertEquals(expected.score(), actual.score(), label + ": score");
        for (String code : expected.penalties().keySet()) {
            assertEquals(expected.penalties().get(code), actual.penalties().get(code),
                    label + ": " + code);
        }
    }
}
