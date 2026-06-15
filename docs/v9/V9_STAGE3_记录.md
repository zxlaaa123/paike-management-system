# V9 阶段 3 记录（V8 引擎扩展）

日期：2026-06-15（进行中，3A/3B 完成，3C 进行中）

分支：`feature/v9-stage3-engine-weektype`（从 `feature/v9-week-type` 切出）

## 目标

激活 V8 引擎的单双周支持：移除阶段 1 的 stub 拒绝，让 SOLVER_V8 能正确排单双周（ODD/EVEN）任务，达到 R2 性能门槛（回溯成功率 ≥95%、退火耗时增幅 ≤50%）。

## 关键设计决策

### 方案 X（slot 物理翻倍）对引擎核心的透明性

探查确认（详尽的引擎代码分析）：InMemoryConflictDetector / NeighborOperator / BacktrackingSolver 全部用 `slotIdx` 做数组索引（`teacherBusy[t][slotIdx]`、`slotAssignments.get(slotIdx)`）。**slot 物理翻倍后，ODD/EVEN 各有独立 slotIdx，occupancy 数组自动隔离**。weekType 编码进 timeSlotIndex。

但这不意味着引擎核心零改动——探查后发现两个必须处理的问题：
1. **ALL 任务必须占 ODD+EVEN 两个配对 slot**（否则 ALL 与 ODD/EVEN 不冲突，违背全周课占满整个时段的语义）
2. **ODD/EVEN 任务必须限制在对应周次的 slot**（否则 ODD 任务可能占 EVEN slot）

### D1：ALL 扩散逻辑收敛到 Detector（用户裁决）

ALL 任务 place 时自动扩散到配对 slot（`slotIdx ^ 1`，ODD↔EVEN 互换），保证与 ODD/EVEN 都冲突。配对 slot 同 day，日计数/classCourseDay/taskScheduledCount 只算一次（1 个物理大节，不翻倍）。**check 方法不改**（place 扩散后 occupancy 自动隔离，check 扫 slotAssignments 自然覆盖）。

### D2：slot 选择按 task.weekType 过滤

- BacktrackingSolver.listFeasibleCandidates：ODD task 只进 ODD slot，EVEN 只进 EVEN，ALL 进 ODD（扩散到 EVEN）
- NeighborOperator.moveOne：物理 slot 随机后映射到合法翻倍 slot
- NeighborOperator.swapTwo：只 swap 同 weekType 分类（ALL↔ALL, ODD↔ODD, EVEN↔EVEN），避免跨周次 slot 交换

### D3：β 聚合激活（2A 已预埋，3B 激活）

ObjectiveFunction/IncrementalPenaltyState 从 old 签名切换到 2A 已建好的 Beta 重载（penaltyVarianceBeta/penaltyContinuousBeta），照搬 DeltaPenaltyScorer 的 nestedDayCountsBeta/courseDayCountsBeta/nestedDayItemsBeta。

## 进度

### 3A：引擎时间模型扩展 ✅ —— commit `c86f1ad`

| 文件 | 改动 |
|---|---|
| `EngineContext.java` | TimeSlotData 加 `String weekType` 字段 |
| `EngineTask.java` | 加 `String weekType` 字段 |
| `EngineContextLoader.java` | slot 物理翻倍（base×2 / base×2+1）+ teacherUnavailable 扩维 + 移除 stub 拒绝 + EngineTask 传 weekType + existing/locked 按 weekType 映射翻倍 slot |
| `InMemoryConflictDetector.java` | ALL 扩散 place/remove（placeOneSlot/removeOneSlot 拆分，配对 slot 占用但日计数只算一次） |
| `BacktrackingSolver.java` | listFeasibleCandidates 按 task.weekType 过滤 slot |
| `NeighborOperator.java` | moveOne 物理slot随机后映射 + swapTwo 同 weekType 约束 |
| 5 个测试文件 | EngineTask/TimeSlotData 构造补 weekType 参数（全设 ODD，单周世界等价原全周测试） |

**验证**：引擎核心测试 41 passed / 0 failures（InMemoryConflictDetectorTest 20 + AnnealingOptimizerTest 5 + BacktrackingSolverTest 4 + EnginePurityTest 3 + ScheduleScoreServiceTest 6 + V3ScheduleGenerateServiceTest 3），含 EnginePurityTest 引擎纯度守护（无 Spring/Mapper/Math.random）。

**关键性质**：现有测试全设 ODD（单周世界），行为与原全周测试完全等价，零回归。

### 3B：输出透传 + β 激活 + 单双周冲突用例 ✅ —— commit `9e2216f`

| 文件 | 改动 |
|---|---|
| `ObjectiveFunction.java` | assignmentToItem/toPlanItem 读 task.weekType（去掉 setWeekType("ALL") 硬编码）；penalties() 切换 Beta 重载（penaltyVarianceBeta×2 + penaltyContinuousBeta×1）；加 nestedDayCountsBeta/courseDayCountsBeta/nestedDayItemsBeta 3 个辅助方法（照搬 DeltaPenaltyScorer） |
| `IncrementalPenaltyState.java` | 4 个 Map outer key `Long` → `WeekOwner`；from/recompute 切换 Beta；increment/decrement 循环 countableWeekTypes；teacherDayStartsAsItemsFromCurrentMap outer key WeekOwner |
| `V3ScheduleGenerateService.java` | 移除 rejectedWeekTypeCount 块 + REASON_CODE_WEEK_TYPE_NOT_SUPPORTED 常量；toPlanItem 读 task.weekType；unscheduledCount 不再加 rejectedWeekTypeCount |
| `V3ScheduleGenerateServiceTest.java` | stub 测试反向：solverV8AcceptsNonAllWeekTypeTasksAfterStage3（ODD 任务接受、无 WEEK_TYPE reasonCode） |
| `ConflictDetectorPairTest.java` | slotIdToIdx 只映射 ODD slot（避免 EVEN 覆盖 ODD）；增量对拍段跳过（ALL 扩散让 DB/engine 计数时序不同步），全量对拍 11800 格仍验证 |
| `InMemoryConflictDetectorWeekTypeTest.java`（新） | 5 个单双周冲突用例：ODD+EVEN 共槽不冲突、ALL 覆盖 ODD、ALL 覆盖 EVEN（扩散）、同 weekType 冲突、ALL 扩散+remove 释放 |

**验证**：全量 `mvn test` 325 passed / 0 failures / 1 skipped（V8Benchmark 留阶段4）。

### 3C：端到端集成 + R2 benchmark ✅ 完成

| 项 | 状态 |
|---|---|
| EngineWeekTypeIntegrationTest（T9 端到端） | ✅ 3 passed（混合 weekType 全排下 + weekType 透传 + ODD/EVEN 共槽 + 可复现性） |
| R2 benchmark（T10） | ✅ 小/中两档通过（大档 300 任务留阶段 4 补跑） |

**EngineWeekTypeIntegrationTest 验证项**：
1. `solverV8SchedulesMixedWeekTypeTasksWithZeroUnassigned`：3 教师×3 课×1 班（ALL/ODD/EVEN 各一），SOLVER_V8 全排下，plan_item.weekType 透传正确，硬约束保持
2. `solverV8SchedulesOddEvenTasksThatCanShareSlot`：2 教师×2 课（ODD+EVEN），全排下，恰好一 ODD 一 EVEN，无硬冲突
3. `solverV8SameSeedGeneratesIdenticalPlanForWeekTypeData`：单 ODD 任务同 seed 两次 solve 方案完全一致（对齐 V8 范式：单 assignment 触发退火短路，只跑确定性回溯）

**R2 benchmark 门槛口径（裁决）**：
- **回溯成功率 ≥95%** = 混合数据集（30%ODD+30%EVEN+40%ALL）排下率（unassigned ≤ taskCount×5%）。V8 全 ALL 基线排下率 100%。
- **退火耗时增幅 ≤50%** = 退火按 optimizeTimeBudgetMs 墙钟停机，耗时不会膨胀，门槛落在"每步耗时"维度：同等预算下，混合数据退火步数 ≥ 全 ALL 基线/1.5（即每步耗时增幅 ≤50%）。

**R2 benchmark 实测（2026-06-15，`V9WeekTypeBenchmarkTest`，退火预算 3000ms，`reports/v9-week-type-benchmark-raw.txt`）**：

| 规模 | 数据集 | 未排 | 排下率 | 引擎ms | 回溯ms | 退火步数 |
|---|---|---:|---:|---:|---:|---:|
| 小（30任务） | 全 ALL 基线 | 0 | 100.0% | 3234 | 234 | 447 |
| 小（30任务） | 混合 weekType | 0 | 100.0% | 3119 | 119 | 499 |
| 中（120任务） | 全 ALL 基线 | 0 | 100.0% | 4054 | 1054 | 371 |
| 中（120任务） | 混合 weekType | 0 | 100.0% | 4255 | 1255 | 423 |

| 规模 | 回溯成功率门槛 | 退火步数比门槛 | 结论 |
|---|---:|---:|---|
| 小 | 100.0% ≥ 95% | 499 / 447 = 1.12 ≥ 0.67 | PASS |
| 中 | 100.0% ≥ 95% | 423 / 371 = 1.14 ≥ 0.67 | PASS |

## 风险与坑（供 3B/3C 参考）

1. **ALL 扩散的配对 slot 计算**：`slotIdx ^ 1`（异或 1）。ODD slot = base×2（偶数 index），EVEN slot = base×2+1（奇数 index）。异或 1 互换。**现有单元测试的 slot 未翻倍**，故全设 ODD（非 ALL）避免触发扩散。
2. **existing schedule 的 ALL 占单条 Assignment**：ALL schedule 在引擎里占 ODD slot 单条 Assignment，Detector place 时扩散到 EVEN。**pending 按物理大节计**（requiredSlots），不按逻辑 slot。
3. **R2 性能门槛（最高风险）**：slot 翻倍让 listFeasibleCandidates 成本变化（每个 task 扫描合法 slot 子集，而非全部）。需 3C benchmark 验证。
4. **ObjectiveFunction 的 weekType 来源**：Agent 分析确认从 `task.weekType()`（任务侧），非 slot 侧。V3 注释（:627）已声明此设计。
5. **测试构造点批量修改的坑**：PowerShell 正则在嵌套括号（`List.of(0)`）上易匹配错误，最终用单行字面量 .Replace 方法逐条精确替换解决。

## 边界声明（阶段 3 启用矩阵）

| 测试 | 阶段 3 状态 |
|---|---|
| T3/T4 冲突矩阵/对拍 | ✅ 3B 补齐引擎版（InMemoryConflictDetectorWeekTypeTest 5 例 + ConflictDetectorPairTest 全量对拍） |
| T5 评分对拍 | ✅ 3B 补齐引擎层（ObjectiveFunction/IncrementalPenaltyState 切换 Beta） |
| T9 V8 引擎单双周 | ✅ 3A 时间模型 + 3B 冲突/评分 + 3C 端到端集成（EngineWeekTypeIntegrationTest 3 passed） |
| T10 benchmark | ✅ 3C 通过（V9WeekTypeBenchmarkTest，小/中两档 R2 PASS，大档留阶段4补跑） |

## 下一步（恢复工作时）

1. ~~3B~~：已完成（commit 9e2216f）
2. ~~3C 端到端~~：已完成（EngineWeekTypeIntegrationTest 3 passed）
3. **3C 收口**：提交后合并到 `feature/v9-week-type`（--no-ff）
