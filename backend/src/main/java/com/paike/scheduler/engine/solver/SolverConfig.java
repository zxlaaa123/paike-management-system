package com.paike.scheduler.engine.solver;

import java.util.Objects;
import java.util.Random;

/**
 * 回溯求解器配置：所有"如何求解"的可调参数。
 * 纯 POJO + record，便于单测构造任意组合。
 *
 * <p>回溯预算/时间预算：触发后剩余任务按贪心 first-fit 收尾，保证总能结束。</p>
 */
public record SolverConfig(
        long seed,
        int maxBacktracks,
        long feasibleTimeBudgetMs,
        boolean greedyFallback
) {

    public static final long DEFAULT_FEASIBLE_TIME_BUDGET_MS = 5_000L;
    public static final int DEFAULT_MAX_BACKTRACKS = 100_000;

    public SolverConfig {
        if (maxBacktracks < 0) {
            throw new IllegalArgumentException("maxBacktracks must be >= 0, got " + maxBacktracks);
        }
        if (feasibleTimeBudgetMs <= 0) {
            throw new IllegalArgumentException("feasibleTimeBudgetMs must be > 0, got " + feasibleTimeBudgetMs);
        }
        Objects.requireNonNull(greedyFallback, "greedyFallback");
    }

    public static SolverConfig defaults() {
        return new SolverConfig(System.nanoTime(), DEFAULT_MAX_BACKTRACKS, DEFAULT_FEASIBLE_TIME_BUDGET_MS, true);
    }

    public static SolverConfig withSeed(long seed) {
        return new SolverConfig(seed, DEFAULT_MAX_BACKTRACKS, DEFAULT_FEASIBLE_TIME_BUDGET_MS, true);
    }

    public Random newRandom() {
        return new Random(seed);
    }
}
