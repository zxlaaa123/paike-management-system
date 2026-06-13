package com.paike.scheduler.engine.solver;

import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineSolution;
import com.paike.scheduler.engine.optimize.AnnealingOptimizer;
import com.paike.scheduler.engine.optimize.NeighborOperator;
import com.paike.scheduler.engine.optimize.ObjectiveFunction;

import java.util.Random;

/**
 * V8 引擎唯一对外入口。薄包装，构造回溯求解器、求解、返回结果。
 *
 * <p>阶段 3 会在 facade 内部"回溯 → 退火"串接；阶段 2 只做回溯。</p>
 */
public final class EngineFacade {

    private EngineFacade() {
    }

    public static EngineSolution solve(EngineContext ctx, SolverConfig config) {
        Random random = config.newRandom();
        BacktrackingSolver solver = new BacktrackingSolver(ctx, config);
        EngineSolution feasible = solver.solve();
        ObjectiveFunction objectiveFunction = new ObjectiveFunction(ctx);
        NeighborOperator neighborOperator = new NeighborOperator(ctx);
        AnnealingOptimizer optimizer = new AnnealingOptimizer(ctx, objectiveFunction, neighborOperator);
        return optimizer.optimize(feasible, config, random);
    }
}
