package com.paike.scheduler.engine.optimize;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineSolution;
import com.paike.scheduler.engine.solver.SolverConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * R4 simulated annealing optimizer. Time is used only as a stop condition.
 */
public final class AnnealingOptimizer {

    private static final int TEMPERATURE_SAMPLE_COUNT = 100;
    private static final double COOLING_RATE = 0.97D;
    private static final double MIN_TEMPERATURE_RATIO = 1e-4D;

    private final EngineContext ctx;
    private final ObjectiveFunction objectiveFunction;
    private final NeighborOperator neighborOperator;

    public AnnealingOptimizer(
            EngineContext ctx,
            ObjectiveFunction objectiveFunction,
            NeighborOperator neighborOperator
    ) {
        this.ctx = Objects.requireNonNull(ctx, "ctx must not be null");
        this.objectiveFunction = Objects.requireNonNull(objectiveFunction, "objectiveFunction must not be null");
        this.neighborOperator = Objects.requireNonNull(neighborOperator, "neighborOperator must not be null");
    }

    public EngineSolution optimize(EngineSolution feasible, SolverConfig config, Random random) {
        Objects.requireNonNull(feasible, "feasible must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(random, "random must not be null");

        ObjectiveFunction objectiveFunction = this.objectiveFunction;
        ObjectiveFunction.ObjectiveValue initialValue = objectiveFunction.evaluate(feasible.assignments());
        if (feasible.assignments().size() < 2 || config.optimizeTimeBudgetMs() <= 0) {
            return withStats(feasible, initialValue, initialValue, 0);
        }

        boolean profile = Boolean.getBoolean("annealing.profile");
        long startedNanos = System.nanoTime();
        long estimateT0Start = System.nanoTime();
        double temperature = estimateInitialTemperature(feasible.assignments(), initialValue.totalPenalty(), random);
        long estimateT0Nanos = System.nanoTime() - estimateT0Start;
        if (temperature <= 0D) {
            return withStats(feasible, initialValue, initialValue, 0);
        }

        // 增量惩罚状态: 每步仅改 Map + 重算 6 个 penalty, 省去 evaluate 每次 groupingBy(items) 的 O(items) 成本。
        // 行为硬保证: state.value() 与 objectiveFunction.evaluate(currentList) 字节级一致
        // (IncrementalPenaltyStateTest 对拍验证), accept/reject 序列与全量路径完全相同。
        List<Assignment> current = new ArrayList<>(feasible.assignments());
        IncrementalPenaltyState state = IncrementalPenaltyState.from(current, objectiveFunction, ctx);
        ObjectiveFunction.ObjectiveValue currentValue = state.value();
        List<Assignment> best = new ArrayList<>(current);
        ObjectiveFunction.ObjectiveValue bestValue = currentValue;
        int steps = 0;
        int iterationsPerTemperature = Math.max(100, ctx.taskCount() * 5);
        double minTemperature = temperature * MIN_TEMPERATURE_RATIO;

        // timing 累加器（纳秒）: 用于定位增量路径每步真实瓶颈。profiling 开关 annealing.profile=true 时输出。
        long tNeighbor = 0L, tFindChanged = 0L, tApply = 0L, tRevert = 0L, tAccept = 0L;

        while (temperature >= minTemperature && elapsedMillis(startedNanos) < config.optimizeTimeBudgetMs()) {
            for (int i = 0; i < iterationsPerTemperature && elapsedMillis(startedNanos) < config.optimizeTimeBudgetMs(); i++) {
                long s0 = System.nanoTime();
                Optional<List<Assignment>> maybeNeighbor = neighborOperator.next(current, random);
                if (maybeNeighbor.isEmpty()) {
                    tNeighbor += System.nanoTime() - s0;
                    continue;
                }
                List<Assignment> neighbor = maybeNeighbor.get();
                tNeighbor += System.nanoTime() - s0;
                // swapTwo 一次改变两个位置, 需对每个 changedIndex 各 apply 一次。
                long s1 = System.nanoTime();
                List<Integer> changedIndices = findAllChangedIndices(current, neighbor);
                if (changedIndices.isEmpty()) {
                    tFindChanged += System.nanoTime() - s1;
                    continue;
                }
                tFindChanged += System.nanoTime() - s1;

                long s2 = System.nanoTime();
                List<BigDecimal> perStepDeltas = new ArrayList<>(changedIndices.size());
                for (int idx : changedIndices) {
                    Assignment removed = current.get(idx);
                    Assignment added = neighbor.get(idx);
                    state.apply(removed, added);
                    perStepDeltas.add(state.totalPenalty().subtract(currentValue.totalPenalty()));
                }
                tApply += System.nanoTime() - s2;

                BigDecimal combinedDelta = perStepDeltas.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                long s3 = System.nanoTime();
                boolean accepted = shouldAccept(combinedDelta, temperature, random);
                if (accepted) {
                    for (int idx : changedIndices) {
                        current.set(idx, neighbor.get(idx));
                    }
                    currentValue = state.value();
                    if (currentValue.totalPenalty().compareTo(bestValue.totalPenalty()) < 0) {
                        best = new ArrayList<>(current);
                        bestValue = currentValue;
                    }
                } else {
                    // 拒绝: 按逆序 revert 多个 apply, 恢复 state 到原状。
                    for (int ri = changedIndices.size() - 1; ri >= 0; ri--) {
                        int idx = changedIndices.get(ri);
                        state.revert(neighbor.get(idx), current.get(idx));
                    }
                }
                long s4 = System.nanoTime();
                if (accepted) {
                    tAccept += s4 - s3;
                } else {
                    tRevert += s4 - s3;
                }
                steps++;
            }
            temperature *= COOLING_RATE;
        }

        if (profile) {
            long totalElapsed = System.nanoTime() - startedNanos;
            long mainNanos = totalElapsed - estimateT0Nanos;
            StringBuilder sb = new StringBuilder("\n[ANNEALING-PROFILE] taskCount=").append(ctx.taskCount())
                    .append(" assignments=").append(feasible.assignments().size())
                    .append(" steps=").append(steps)
                    .append(" budgetMs=").append(config.optimizeTimeBudgetMs())
                    .append(" elapsedMs=").append(totalElapsed / 1_000_000L).append('\n');
            sb.append("[ANNEALING-PROFILE] estimateT0=").append(estimateT0Nanos / 1_000_000L).append("ms")
                    .append(" | mainLoop=").append(mainNanos / 1_000_000L).append("ms\n");
            if (steps > 0) {
                sb.append("[ANNEALING-PROFILE] per-step(us, avg over ").append(steps).append("): ");
                sb.append("neighbor=").append(usPerStep(tNeighbor, steps));
                sb.append(" findChanged=").append(usPerStep(tFindChanged, steps));
                sb.append(" apply=").append(usPerStep(tApply, steps));
                sb.append(" accept=").append(usPerStep(tAccept, steps));
                sb.append(" revert=").append(usPerStep(tRevert, steps)).append('\n');
            }
            System.err.println(sb);
        }

        return new EngineSolution(
                List.copyOf(best),
                feasible.unassignedSlots(),
                new EngineSolution.SolverStats(
                        feasible.stats().backtracks(),
                        steps,
                        initialValue.score().doubleValue(),
                        bestValue.score().doubleValue()));
    }

    private double estimateInitialTemperature(
            List<Assignment> current,
            BigDecimal currentPenalty,
            Random random
    ) {
        BigDecimal worseningSum = BigDecimal.ZERO;
        int worseningCount = 0;
        for (int i = 0; i < TEMPERATURE_SAMPLE_COUNT; i++) {
            Optional<List<Assignment>> maybeNeighbor = neighborOperator.next(current, random);
            if (maybeNeighbor.isEmpty()) {
                continue;
            }
            BigDecimal delta = objectiveFunction.evaluate(maybeNeighbor.get()).totalPenalty().subtract(currentPenalty);
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                worseningSum = worseningSum.add(delta);
                worseningCount++;
            }
        }
        if (worseningCount == 0) {
            return 1D;
        }
        double avgWorsening = worseningSum.divide(BigDecimal.valueOf(worseningCount), 8, java.math.RoundingMode.HALF_UP)
                .doubleValue();
        return avgWorsening / Math.log(2D);
    }

    /**
     * 找出 current 与 neighbor 之间的所有 changed indices（moveOne 通常 1 个，swapTwo 通常 2 个）。
     * 由于 Assignment 是 record 且 value-equal, 这里用 multiset 思路: 对每个 neighbor 元素
     * 优先匹配 current 同 index（同位置替换是最高频情况），剩余按出现顺序找 changed。
     */
    private static List<Integer> findAllChangedIndices(List<Assignment> current, List<Assignment> neighbor) {
        if (current.size() != neighbor.size()) {
            return List.of();
        }
        int n = current.size();
        boolean[] currentMatched = new boolean[n];
        int[] neighborMatchedAt = new int[n];
        Arrays.fill(neighborMatchedAt, -1);
        // 第一遍: 同 index 且 equal 视为未变
        for (int i = 0; i < n; i++) {
            if (current.get(i).equals(neighbor.get(i))) {
                currentMatched[i] = true;
                neighborMatchedAt[i] = i;
            }
        }
        // 第二遍: 找 changed indices
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (currentMatched[i]) {
                continue;
            }
            // current[i] 在 neighbor 中没匹配（除已 mark 的）→ current[i] 是被移走的（removed）
            int foundAt = -1;
            for (int j = 0; j < n; j++) {
                if (neighborMatchedAt[j] != -1) {
                    continue;
                }
                if (current.get(i).equals(neighbor.get(j))) {
                    foundAt = j;
                    break;
                }
            }
            if (foundAt < 0) {
                changed.add(i);
            } else {
                neighborMatchedAt[foundAt] = i;
            }
        }
        return changed;
    }

    private static boolean shouldAccept(BigDecimal delta, double temperature, Random random) {
        if (delta.compareTo(BigDecimal.ZERO) < 0) {
            return true;
        }
        if (temperature <= 0D) {
            return false;
        }
        return random.nextDouble() < Math.exp(-delta.doubleValue() / temperature);
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    /** profiling 辅助: 某段累计纳秒折算成每步微秒（3 位小数）。 */
    private static String usPerStep(long totalNanos, int steps) {
        return String.format(java.util.Locale.ROOT, "%.2f", totalNanos / 1000.0 / steps);
    }

    private static EngineSolution withStats(
            EngineSolution feasible,
            ObjectiveFunction.ObjectiveValue initialValue,
            ObjectiveFunction.ObjectiveValue finalValue,
            int annealingSteps
    ) {
        return new EngineSolution(
                feasible.assignments(),
                feasible.unassignedSlots(),
                new EngineSolution.SolverStats(
                        feasible.stats().backtracks(),
                        annealingSteps,
                        initialValue.score().doubleValue(),
                        finalValue.score().doubleValue()));
    }
}
