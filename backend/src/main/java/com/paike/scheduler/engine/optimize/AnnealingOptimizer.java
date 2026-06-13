package com.paike.scheduler.engine.optimize;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineSolution;
import com.paike.scheduler.engine.solver.SolverConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
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

        ObjectiveFunction.ObjectiveValue initialValue = objectiveFunction.evaluate(feasible.assignments());
        if (feasible.assignments().size() < 2 || config.optimizeTimeBudgetMs() <= 0) {
            return withStats(feasible, initialValue, initialValue, 0);
        }

        long startedNanos = System.nanoTime();
        double temperature = estimateInitialTemperature(feasible.assignments(), initialValue.totalPenalty(), random);
        if (temperature <= 0D) {
            return withStats(feasible, initialValue, initialValue, 0);
        }

        List<Assignment> current = new ArrayList<>(feasible.assignments());
        ObjectiveFunction.ObjectiveValue currentValue = initialValue;
        List<Assignment> best = new ArrayList<>(current);
        ObjectiveFunction.ObjectiveValue bestValue = initialValue;
        int steps = 0;
        int iterationsPerTemperature = Math.max(100, ctx.taskCount() * 5);
        double minTemperature = temperature * MIN_TEMPERATURE_RATIO;

        while (temperature >= minTemperature && elapsedMillis(startedNanos) < config.optimizeTimeBudgetMs()) {
            for (int i = 0; i < iterationsPerTemperature && elapsedMillis(startedNanos) < config.optimizeTimeBudgetMs(); i++) {
                Optional<List<Assignment>> maybeNeighbor = neighborOperator.next(current, random);
                if (maybeNeighbor.isEmpty()) {
                    continue;
                }
                List<Assignment> neighbor = maybeNeighbor.get();
                ObjectiveFunction.ObjectiveValue neighborValue = objectiveFunction.evaluate(neighbor);
                BigDecimal delta = neighborValue.totalPenalty().subtract(currentValue.totalPenalty());
                if (shouldAccept(delta, temperature, random)) {
                    current = neighbor;
                    currentValue = neighborValue;
                    if (currentValue.totalPenalty().compareTo(bestValue.totalPenalty()) < 0) {
                        best = new ArrayList<>(current);
                        bestValue = currentValue;
                    }
                }
                steps++;
            }
            temperature *= COOLING_RATE;
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
