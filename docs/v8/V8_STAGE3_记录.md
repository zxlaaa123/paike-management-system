# V8 阶段 3 记录

日期：2026-06-13

分支：`feature/v8-stage3-annealing`

## 做了什么

### 1. engine/optimize/ - 模拟退火优化器

新增纯 Java 包 `com.paike.scheduler.engine.optimize`：

| 文件 | 说明 |
| --- | --- |
| `ObjectiveFunction.java` | 使用 `ScoringFunctions` 离线 penalty 系列，按 `schedule_rule_weight` 的 SOFT 权重计算目标函数；内部可输出逐项 penalty、总 penalty、折算 score |
| `NeighborOperator.java` | 70% 单点移动 + 30% 两点交换；锁定/既有课表作为 detector baseline，不进入邻域；生成邻居必须通过 `InMemoryConflictDetector` |
| `AnnealingOptimizer.java` | 按 V8_02 第 5 节实现：采样估 T0、`0.97` 降温、每温度层 `max(100, taskCount*5)`、时间预算只作停止条件、返回历史最优 |

随机性纪律：

- `EngineFacade` 每次 solve 只从 `SolverConfig` 创建一个 `Random(seed)` 实例。
- 退火和邻域算子只接收该实例，不自行创建随机源。
- `System.nanoTime()` 仅用于预算停止判断，不参与解的选择。

### 2. SOLVER_V8 流程接入

`EngineFacade.solve` 现在执行：

`BacktrackingSolver` 可行解 -> `AnnealingOptimizer` 优化 -> `EngineSolution` 返回。

`EngineSolution` 新增 `SolverStats`：

- `backtracks`
- `annealingSteps`
- `initialScore`
- `finalScore`

`V3ScheduleGenerateService` 性能基线 extra 现在写入：

```json
{
  "seed": 42,
  "timeBudgetMs": 1000,
  "scheduledCount": 1,
  "unassignedCount": 0,
  "backtracks": 0,
  "annealingSteps": 0,
  "initialScore": 100.0,
  "finalScore": 100.0
}
```

现有 `performance_baseline` 有 `extra_json` 字段，所以未走 generate log 降级。

### 3. 与 rescore 同源

`EngineContext` 新增 `afternoonStartPeriod`，由 `EngineContextLoader` 从 `ScheduleThresholdProperties` 注入，避免 engine 内硬编码上午/下午边界。

`ObjectiveFunction` 使用离线公式：

- `penaltyVariance`
- `penaltyDuplicateCourse`
- `penaltyContinuous`
- `penaltyClassroomUtilization`
- `penaltyMorningPriority`

只使用离线 penalty 系列，不混用在线 candidate 系列。

### 4. 测试补齐

新增/扩展测试：

| 测试 | 覆盖 |
| --- | --- |
| `AnnealingOptimizerTest.sameSeedSameDataProducesIdenticalAssignments` | 同 seed 同数据，engine 输出 assignments 完全一致 |
| `AnnealingOptimizerTest.annealingOutputDoesNotWorsenObjectiveAndKeepsHardConstraints` | 退火目标函数不劣化，最终解无硬违规 |
| `AnnealingOptimizerTest.objectiveFunctionMatchesOfflineScoringPenaltySeries` | ObjectiveFunction 逐项 penalty 与 `ScoringFunctions` 离线公式对拍 |
| `AnnealingOptimizerTest.neighborKeepsFeasibleSolutionAndDoesNotMoveLockedBaseline` | 邻域算子可行，锁定项不参与邻域 |
| `AnnealingOptimizerTest.annealingHistoryNeverReturnsHardViolation` | 退火结果逐项通过冲突检测 |
| `ScheduleScoreServiceTest.rescore_detailsMatchV8ObjectiveFunctionWeightedPenalties` | ObjectiveFunction 的 `weight × penalty` 与 rescore 写入的 `ScheduleScoreDetail.score` 对拍 |
| `V8SolverGenerateIntegrationTest.solverV8SameSeedGeneratesIdenticalPersistedPlanItems` | 同 seed 经 `SOLVER_V8` 两次生成，落库 plan items 签名完全一致 |
| `V8SolverGenerateIntegrationTest.assertSolverPerformanceRecorded` | 性能 extra 包含 seed/backtracks/annealingSteps/initialScore/finalScore |

## 附带修复

为满足完整 `mvn test`：

1. `EngineContextLoader` 移除手写 `deleted=0` 条件，交给 MyBatis Plus 逻辑删除插件处理。
2. `SchedulePlanService.applyPlanInternal` 恢复按学期清空正式课表，并保留按 old applied planId 逻辑删除的源码契约，满足 M39/M41 架构测试。

## 测试结果

| 命令 | 结果 |
| --- | --- |
| `cd D:\paike\backend; mvn -q '-Dtest=com.paike.scheduler.engine.optimize.AnnealingOptimizerTest,com.paike.scheduler.engine.solver.BacktrackingSolverTest,com.paike.scheduler.engine.conflict.InMemoryConflictDetectorTest,com.paike.scheduler.service.ScheduleScoreServiceTest,com.paike.scheduler.service.V3ScheduleGenerateServiceTest' test` | exit=0 |
| `cd D:\paike\backend; mvn -q '-Dtest=com.paike.scheduler.engine.EnginePurityTest,com.paike.scheduler.engine.optimize.AnnealingOptimizerTest,com.paike.scheduler.engine.solver.BacktrackingSolverTest,com.paike.scheduler.engine.conflict.InMemoryConflictDetectorTest,com.paike.scheduler.service.ScheduleScoreServiceTest,com.paike.scheduler.service.V3ScheduleGenerateServiceTest,com.paike.scheduler.service.V8SolverGenerateIntegrationTest' test` | exit=0 |
| `cd D:\paike\backend; mvn -q '-Dtest=com.paike.scheduler.service.SchedulePlanServiceTest,com.paike.scheduler.architecture.M39ManualDeletedUpdateInvestigationTest,com.paike.scheduler.architecture.M41DeletedZeroConditionCleanupTest' test` | exit=0 |
| `cd D:\paike\backend; mvn test` | exit=0；Tests run: 274, Failures: 0, Errors: 0, Skipped: 0；BUILD SUCCESS |

前端未改动，未运行 `vue-tsc`。

## 未完成 / 注意

- 本阶段未做浏览器 E2E；阶段 3 主要变更在后端引擎和服务集成。
- `initialScore/finalScore` 记录为按 rescore 口径折算后的分数（`100 - weightedPenalty`），退火内部仍以 weighted penalty 越小越好作为目标函数。
