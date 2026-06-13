# V8 阶段 2 记录

日期：2026-06-13

分支：`feature/v8-stage2-solver`

## 做了什么

### 1. engine/solver/ - 回溯求解器

| 文件 | 说明 |
|---|---|
| `BacktrackingSolver.java` | MRV 变量排序 + 稳定候选排序 + 回溯预算/时间预算 + 贪心收尾 |
| `SolverConfig.java` | seed / maxBacktracks / feasibleTimeBudgetMs / greedyFallback |
| `EngineFacade.java` | 阶段 2 对外入口：`EngineContext + SolverConfig -> EngineSolution` |

接手时已存在上述 3 个草稿文件。本轮修正：

- 候选为空时必须向父节点返回 `false` 触发回溯，不能直接跳过，否则“贪心必败但有解”场景会错误 partial。
- 移除比较器里的随机读数，改为稳定时段/教室索引排序，避免违反 comparator 传递性。
- 删除 engine 包注释中的 `Math.random()` 文本，避免纯度测试误伤。
- 修正 `EngineContextLoader` 跳过无效任务后的 `EngineTask.index` 稠密索引。

### 2. SOLVER_V8 后端接入

| 文件 | 说明 |
|---|---|
| `V3ScheduleGenerateService.java` | `SOLVER_V8` 分支：装载 `EngineContext`、调用 `EngineFacade`、转换 `SchedulePlanItem`、保存未排任务、rescore、性能记录 |
| `ScheduleGenerateRequest.java` | 新增 `solverSeed` / `solverTimeBudgetMs` |
| `PerformanceBaselineService.java` | 新增 `V8_SOLVER_GENERATE` 操作类型 |
| `V3ScheduleGenerateServiceTest.java` | mock 级接入守卫：验证 V8 走 engine path、落 plan item、跳过旧 referenceLoader、记录性能 |
| `V8SolverGenerateIntegrationTest.java` | 真实 MySQL 集成测试：插入独立数据，验证 `SOLVER_V8` 与旧 `COMPREHENSIVE` 都能生成并落库 |

约束：

- 旧四策略路径不变。
- `/multiple` 默认策略列表不加入 `SOLVER_V8`。
- `solverTimeBudgetMs` 服务端钳制到 `[1000, 60000]`。

### 3. 前端最小接入

| 文件 | 说明 |
|---|---|
| `frontend/src/api/scheduleGenerate.ts` | 请求类型新增 solver 可选字段 |
| `frontend/src/utils/status.ts` | `SOLVER_V8 -> 智能求解` |
| `ScheduleGenerateView.vue` | 策略选项新增智能求解；单方案选择 V8 时显示 seed / 时间预算输入 |
| `SchedulePlanView.vue` | 策略筛选新增智能求解 |

## 测试结果

| 命令 | 结果 |
|---|---|
| `cd backend; mvn -q -DskipTests compile` | 退出码 0 |
| `cd backend; mvn -q -Dtest=com.paike.scheduler.engine.solver.BacktrackingSolverTest,com.paike.scheduler.engine.EnginePurityTest test` | 退出码 0 |
| `cd backend; mvn -q -Dtest=com.paike.scheduler.engine.solver.BacktrackingSolverTest,com.paike.scheduler.engine.EnginePurityTest,com.paike.scheduler.service.V3ScheduleGenerateServiceTest test` | 退出码 0 |
| `cd backend; mvn -q -Dtest=com.paike.scheduler.engine.conflict.InMemoryConflictDetectorTest,com.paike.scheduler.engine.solver.BacktrackingSolverTest,com.paike.scheduler.engine.EnginePurityTest,com.paike.scheduler.service.V3ScheduleGenerateServiceTest test` | 退出码 0 |
| `cd backend; mvn -q -Dtest=com.paike.scheduler.engine.conflict.ConflictDetectorPairTest test`（`DB_USERNAME=root` / `DB_PASSWORD=123456` / 测试 `JWT_SECRET`） | surefire：Tests run: 1, Failures: 0, Errors: 0, Skipped: 0，耗时 201.2s |
| `cd backend; mvn -q -Dtest=com.paike.scheduler.service.V8SolverGenerateIntegrationTest test`（同上 DB/JWT 环境） | surefire：Tests run: 1, Failures: 0, Errors: 0, Skipped: 0，耗时 57.43s |
| `cd frontend; npx vue-tsc -b --pretty false` | 退出码 0 |
| HTTP 冒烟：启动后端 8090，Bearer JWT 调 `/api/v3/schedule-generate`，分别提交 `SOLVER_V8` 与 `COMPREHENSIVE` | 两次响应均成功；V8 planId=5，old planId=6；两者 `scheduledCount=1`、`unassignedCount=0`、`conflictCount=0`、`totalScore=99.52`；`schedule_plan_item`、`performance_baseline_record(V8_SOLVER_GENERATE)` 均查到；测试数据已清理 |
| `git diff --check` | 退出码 0 |

## 未完成 / 阻塞

浏览器 E2E 未跑；本轮已完成后端真实 HTTP 冒烟。前端已通过 `vue-tsc`，页面级交互可在下一轮统一 E2E 收口时补跑。

## 偏离文档之处

1. 阶段 2 暂未加入全链路浏览器 E2E。原因：项目约束要求后端由用户在独立 PowerShell 终端手动启动，当前会话未启动后端。
2. 性能记录 extra 当前写入 seed、timeBudgetMs、scheduledCount、unassignedCount；backtracks 仍未从 `EngineFacade.solve` 暴露。阶段 3 若需要记录 backtracks，可扩展 facade 返回统计对象。
