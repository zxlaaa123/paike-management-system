# V9 阶段 3 记录（V8 引擎扩展）

日期：2026-06-14（进行中，3A 完成，3B/3C 未开始）

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

### 3B：输出透传 + β 激活 + 三路对拍 ⏳ 未开始

| 文件 | 待改 |
|---|---|
| `ObjectiveFunction.java` | assignmentToItem/toPlanItem 读 task.weekType（去掉 setWeekType("ALL") 硬编码）；penalties() 切换 Beta 重载；加 nestedDayCountsBeta 等 3 个辅助方法 |
| `IncrementalPenaltyState.java` | 4 个 Map outer key → WeekOwner；from/recompute/increment/decrement 切换 |
| `V3ScheduleGenerateService.java` | 移除 rejectedWeekTypeCount 块（552-572）+ 修正 589/598/601/605 引用；toPlanItem:628 读 task.weekType |
| 测试 | InMemoryConflictDetectorTest 单双周用例 + ConflictDetectorPairTest 三路对拍 + T5 引擎层对拍 |

### 3C：端到端集成 + R2 benchmark ⏳ 未开始

| 项 | 待做 |
|---|---|
| EngineWeekTypeIntegrationTest | 端到端：含 ODD+EVEN 任务数据集，SOLVER_V8 全部排下（0 unassigned），方案 item weekType 正确 |
| R2 benchmark | 混合数据集（30% ODD + 30% EVEN + 40% ALL），回溯成功率 ≥95%、退火耗时增幅 ≤50%。**渐进策略**：先跑通，不达标再调优 |

## 风险与坑（供 3B/3C 参考）

1. **ALL 扩散的配对 slot 计算**：`slotIdx ^ 1`（异或 1）。ODD slot = base×2（偶数 index），EVEN slot = base×2+1（奇数 index）。异或 1 互换。**现有单元测试的 slot 未翻倍**，故全设 ODD（非 ALL）避免触发扩散。
2. **existing schedule 的 ALL 占单条 Assignment**：ALL schedule 在引擎里占 ODD slot 单条 Assignment，Detector place 时扩散到 EVEN。**pending 按物理大节计**（requiredSlots），不按逻辑 slot。
3. **R2 性能门槛（最高风险）**：slot 翻倍让 listFeasibleCandidates 成本变化（每个 task 扫描合法 slot 子集，而非全部）。需 3C benchmark 验证。
4. **ObjectiveFunction 的 weekType 来源**：Agent 分析确认从 `task.weekType()`（任务侧），非 slot 侧。V3 注释（:627）已声明此设计。
5. **测试构造点批量修改的坑**：PowerShell 正则在嵌套括号（`List.of(0)`）上易匹配错误，最终用单行字面量 .Replace 方法逐条精确替换解决。

## 边界声明（阶段 3 启用矩阵）

| 测试 | 阶段 3 状态 |
|---|---|
| T3/T4 冲突矩阵/对拍 | ⏳ 3B 补齐引擎版（三路对拍） |
| T5 评分对拍 | ⏳ 3B 补齐引擎层 |
| T9 V8 引擎单双周 | ⏳ 3A 部分完成（时间模型），3B/3C 完整 |
| T10 benchmark | ⏳ 3C 启用 |

## 下一步（恢复工作时）

1. **3B**：ObjectiveFunction 输出透传（去掉 3 处 setWeekType("ALL") 硬编码）+ β 激活 + V3 移除 rejectedWeekTypeCount
2. **3B 测试**：InMemoryConflictDetector 单双周用例 + 三路对拍 + T5 引擎层对拍
3. **3C**：EngineWeekTypeIntegrationTest 端到端 + R2 benchmark（渐进策略）
4. 提交后合并到 `feature/v9-week-type`
