# V8 API 接口设计

日期：2026-06-12

原则：**不新增端点、不新增表**。V8 全部通过现有 V3 接口的参数扩展接入。

## 1. 方案生成（扩展现有接口）

### POST `/api/v3/schedule-generate`

请求体（`ScheduleGenerateRequest`）新增可选字段：

```jsonc
{
  "semesterId": 1,
  "planName": "智能求解方案-0612",
  "strategyType": "SOLVER_V8",     // 新增策略码
  "solverSeed": 42,                 // 可选；缺省由后端随机生成并写入 generate log
  "solverTimeBudgetMs": 15000      // 可选；缺省 15000，服务端钳制到 [1000, 60000]
}
```

- 旧策略（`TEACHER_PRIORITY` / `CLASS_BALANCE` / `CLASSROOM_UTILIZATION` / `COMPREHENSIVE`）忽略 solver 字段，行为不变。
- 响应结构不变（`ScheduleGenerateResult`），`strategyType` 回显 `SOLVER_V8`。

### POST `/api/v3/schedule-generate/multiple`

`strategyTypes` 数组允许包含 `SOLVER_V8`。**缺省策略列表不变**（不把 SOLVER_V8 加进默认四策略，避免改变现有 E2E 行为和批量耗时）。

## 2. 下游接口（零改动，自动兼容）

以下接口因 V8 方案与旧方案同表同结构，无需任何修改：

| 接口 | 说明 |
|---|---|
| `GET /api/v3/schedule-plans`（列表/详情/items/logs/unassigned-*） | SOLVER_V8 方案正常展示 |
| `POST /api/v3/schedule-plans/compare` | 可与旧策略方案对比 |
| `POST /api/v3/schedule-plans/{id}/rescore` | 评分口径与引擎目标函数同源 |
| `PUT /api/v3/schedule-plan-items/{itemId}/adjust` | 手动微调照常 |
| 方案应用 / 回滚 / 锁定 | 照常 |
| V5 修复流程 | 照常（理论上 V8 方案需要修复的更少） |

## 3. 校验与错误

- 未知 `strategyType` 的现有兜底行为保持：`normalizeStrategyType` 目前对任意非空字符串放行（trim 后透传）。**V8 不收紧该行为**（收紧属于行为变更，超出本版范围），仅保证 `SOLVER_V8` 走新分支、其余字符串维持现状。
- `solverTimeBudgetMs` 超出 [1000, 60000] 时静默钳制，不报错。
- `solverSeed` 任意 long 合法。
- 引擎异常 → 现有全局异常处理 + 事务回滚 + `system_audit_log` 失败记录，错误码沿用现有体系（不新增错误码；若执行中发现必须新增，先在 `V8_06` 决策升级流程中确认）。

## 4. 性能基线记录

复用 `PerformanceBaselineService.recordSafely`，操作类型沿用现有方案生成的 OP 常量；`extra`（JSON 字符串字段，如现有结构支持）记录：

```json
{ "seed": 42, "backtracks": 1532, "annealingSteps": 84000, "initialScore": 78.5, "finalScore": 91.2 }
```

若现有 `performance_baseline` 无可用扩展字段，则把该 JSON 写入 generate log 的消息体，**不为此加列**。

## 5. 前端改动清单

| 文件 | 改动 |
|---|---|
| `frontend/src/api/scheduleGenerate.ts` | 请求接口类型加 `solverSeed?` / `solverTimeBudgetMs?` |
| `frontend/src/utils/status.ts` | `strategyText` 加 `SOLVER_V8 → 智能求解` |
| 方案生成视图（策略下拉所在页面） | options 加一项；选中 `SOLVER_V8` 时可选展示种子/时间预算两个高级输入（折叠，默认不填） |

不加路由、不加页面、不动 `components.d.ts` 之外的全局声明。
