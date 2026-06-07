# V6 文档适用性审查（2026-06-07）

## 1. 结论

`docs/v6` 不能原样作为当前开工指导文档使用，需要先做一次状态校准。

原因：V6 文档大多写于 2026-05-26，当时结论是“审计日志最小闭环尚未实现”。但 Git 历史显示 2026-05-26 到 2026-05-28 已经完成了一部分 V6 审计日志实现，随后项目转入较大规模 bug/security/perf/stability 修复。因此 V6 文档的方向仍有价值，但实现状态、第一阶段任务拆分、验收口径已经部分过时。

当前判断：

- V6 不是完整实现状态。
- V6 也不是纯草案状态。
- 当前实际状态是：V6 阶段 1“审计日志最小闭环”已启动并完成后端一部分，但覆盖不完整，前端未落地。

## 2. 当前代码事实

已存在：

| 项 | 当前状态 |
|---|---|
| `system_audit_log` | 已存在 |
| `backend/src/main/resources/db/v12_system_audit_log.sql` | 已存在 |
| `SystemAuditLog` | 已存在 |
| `SystemAuditLogMapper` | 已存在 |
| `SystemAuditLogService` | 已存在 |
| `SystemAuditLogController` | 已存在 |
| `GET /api/v6/audit-logs` | 已存在 |
| `GET /api/v6/audit-logs/{id}` | 已存在 |
| `SystemAuditLogServiceTest` | 已存在 |
| 前端 `/v6/audit-logs` 页面 | 未实现 |

仍未实现：

| 文档规划项 | 当前状态 |
|---|---|
| 回归测试中心 `regression_test_run` / `regression_test_case_result` | 未实现 |
| 数据一致性检查中心 `data_consistency_check_run` / `data_consistency_issue` | 未实现 |
| 性能基线中心 `performance_baseline_record` | 未实现 |
| 错误码中心 `api_error_code` | 未实现 |
| V6 前端治理页面集合 | 基本未实现 |

已有相近能力：

| 能力 | 当前载体 |
|---|---|
| V5 一致性检查基础 | `ScheduleConsistencyCheck`、`V5ConsistencyCheckService` |
| V5 回归基础记录 | `ScheduleRegressionTest` |
| V5 修复任务 | `ScheduleRepairTask` |
| V5 修复建议 | `ScheduleRepairSuggestion` |
| V5 候选位置 | `ScheduleCandidatePosition` |
| V5 试算对比 | `ScheduleOptimizationCompare` |

## 3. Git 历史校准

V6 相关关键提交：

| 提交 | 日期 | 含义 |
|---|---|---|
| `60eab13` | 2026-05-26 | `docs(v6): 校准 V6 当前规划` |
| `eb80ae0` | 2026-05-26 | `docs(v6): 记录阶段零基线检查结果` |
| `2f6848e` | 2026-05-26 | `feat(v6): 建立审计日志最小闭环` |
| `51b65ce` | 2026-05-27 | `feat(v6): 记录课程锁定审计日志` |
| `af413be` | 2026-05-28 | `fix(audit): 记录方案应用失败审计` |

之后 2026-05-27 到 2026-05-29 出现大量 bug/security/perf/stability 修复提交。说明用户记忆正确：阶段六做了一点，然后被 bug 修复主线打断。

## 4. 审计覆盖现状

已接入审计：

| 服务 | 覆盖内容 |
|---|---|
| `SchedulePlanService` | 方案应用、方案回滚、方案应用失败审计 |
| `V4ScheduleLockService` | 锁定、解除锁定 |

未接入审计但应纳入 V6 阶段 1：

| 服务 | 写操作 | 建议优先级 |
|---|---|---|
| `ScheduleService` | 手动排课新增、删除 | P0 |
| `V4ScheduleAdjustmentService` | V4 局部调整应用 | P0 |
| `V5SimulationService` | 应用试算方案 | P0 |
| `V4ScheduleReplanService` | 创建局部重排方案 | P1 |
| `V5SimulationService` | 试算生成、局部重排试算 | P1 |
| `AutoScheduleService` | 自动排课批次生成正式排课 | P1 |
| `V5RepairSuggestionService` | 修复建议生成、标记试算、任务建议状态更新 | P2 |

## 5. V6 文档逐类适用性

### 5.1 仍可沿用

这些内容方向仍正确：

- V6 不推翻 V1-V5，只做治理、审计、回归、性能、错误码增强。
- 正式课表写入必须显式触发，并经过后端二次校验。
- 自动排课、局部重排、智能修复不能绕过方案/试算机制直接污染正式课表。
- 评分、风险、修复建议、审计日志必须能追踪到 `planId` 或明确标记为正式课表级操作。
- 审计日志最小闭环仍适合作为 V6 第一阶段。
- 回归测试中心、数据一致性检查中心、性能基线、错误码中心仍适合作为后续阶段。

### 5.2 部分过时

这些说法需要修订：

- “当前未发现统一审计日志表/API”
- “`system_audit_log` 未实现”
- “当前代码没有 `/api/v6` 接口”
- “V6 第一阶段首个实现切片：新增表与只读查询能力”

准确说法应改为：

- `system_audit_log` 与只读查询接口已实现。
- V6 第一阶段已启动，但审计覆盖面不完整。
- 后续第一任务不是“从零新增审计表”，而是“补齐关键写路径审计覆盖 + 前端只读页面”。

### 5.3 仍是未来规划

这些模块目前仍停留在规划状态，文档可继续作为方向参考，但不能当成已实现能力：

- 回归测试中心
- 数据一致性检查中心
- 性能基线中心
- 错误码与提示中心
- 数据库迁移治理页面
- V6 治理大屏

## 6. 不建议直接执行的动作

不要直接按旧 V6 文档从头新增 `system_audit_log`。表、实体、服务、控制器、接口已经存在。

不要直接做完整 V6 前端治理大屏。当前后端审计覆盖还不完整，先做大页面会暴露半成品数据。

不要先做回归测试中心、性能基线、错误码中心。这些是后续治理模块，当前更急的是把已经启动的审计日志阶段收口。

不要把 V6 文档里“数据库脚本编号”和“产品版本阶段”混为一谈。当前存在 `v12_system_audit_log.sql` 等迁移脚本，不代表产品 V12，也不代表完整 V6。

## 7. 建议下一步

第一步：修订 V6 文档状态。（已在本分支完成）

- 更新 `V6_10_现状校准与执行建议.md`。
- 更新 `V6_03_数据库扩展设计.md` 中 `system_audit_log` 的状态。
- 更新 `V6_04_API接口设计.md`，标记审计日志查询接口已实现。
- 更新 `V6_05_前端页面设计.md`，标记前端审计页未实现。

第二步：补 V6 阶段 1 后端审计覆盖。

- P0：`ScheduleService.create/delete`
- P0：`V4ScheduleAdjustmentService.applyAdjustment`
- P0：`V5SimulationService.apply`
- 每个路径补成功审计和失败审计。
- 每个路径补服务层单测。

第三步：补前端审计日志只读页。

- 路由：`/v6/audit-logs`
- API：查询列表、查询详情
- 筛选：操作类型、学期、方案、成功/失败、时间范围
- 不做大屏，不做复杂统计。

第四步：再决定是否进入 V6 第二阶段。

候选顺序：

1. 回归测试中心
2. 数据一致性检查中心
3. 性能基线中心
4. 错误码与提示中心

## 8. 最终判断

V6 文档“方向适用，状态过时”。继续开发前必须先更新文档状态，否则容易重复造已存在的审计表/API，或者误以为 V6 完全没有开始。

推荐本分支下一步只做文档状态修订，不碰功能代码。等文档校准后，再开实现分支补审计覆盖。
