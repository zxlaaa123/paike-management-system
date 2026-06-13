package com.paike.scheduler.engine.solver;

import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineSolution;

/**
 * V8 引擎唯一对外入口。薄包装，构造回溯求解器、求解、返回结果。
 *
 * <p>阶段 3 会在 facade 内部"回溯 → 退火"串接；阶段 2 只做回溯。</p>
 */
public final class EngineFacade {

    private EngineFacade() {
    }

    public static EngineSolution solve(EngineContext ctx, SolverConfig config) {
        BacktrackingSolver solver = new BacktrackingSolver(ctx, config);
        return solver.solve();
    }
}
