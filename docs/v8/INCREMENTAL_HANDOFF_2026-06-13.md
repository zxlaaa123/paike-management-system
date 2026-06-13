# #1 退火增量打分 — 今日进度与明日交接

日期：2026-06-13
分支：当前在 `main`（V8 perf-tuning + #2 #3 已合入并 push），但 incremental 改动**未 commit**（生产代码已改，全量测试 279/0/0 但 benchmark 数据异常）

---

## 0. TL;DR

**退火引擎已切换到增量路径**，所有行为不变硬保证测试（incremental 对拍 + 退火 5/5 同 seed 同数据）全绿。但 benchmark 实测大规模 V8 分数 87.70 → 74.28（-13 分），退火步数从预期 1000+ 降到 71 步。**问题大概率在增量路径每步 ~140ms，导致 10s 预算内主循环只跑 71 步**——退火没收敛到原解。

**今晚不要继续追**，明天接手的同学先读这一段定位方向。

---

## 1. 现状（在 main 上的工作树，未 commit）

### 已改的代码

| 文件 | 状态 |
|---|---|
| `backend/.../engine/optimize/IncrementalPenaltyState.java` | 新建（305 行），包内 final，持有 6 个聚合 Map + 6 个 cached penalty + afternoonCount；`apply(removed, added)` / `revert(added, removed)` |
| `backend/.../engine/optimize/ObjectiveFunction.java` | 加包内 static `assignmentToItem(ctx, assignment)` 与 `weightFor(ruleCode)`（给 IncrementalPenaltyState 复用，不动 evaluate 行为） |
| `backend/.../engine/optimize/AnnealingOptimizer.java` | `optimize()` 改用 IncrementalPenaltyState 主循环，`estimateInitialTemperature` 仍用全量 evaluate；新加 `findAllChangedIndices`（处理 swapTwo 一次改两个位置） |
| `backend/.../service/V8BenchmarkComparisonTest.java` | 跑 annealingSteps 计数、StrategyRun 加字段 |

### 关键设计点（必读）

**IncrementalPenaltyState 不持有 assignments 列表**，持有者是 AnnealingOptimizer（用户提到的"状态不持有 list"模式）。state 只管 Map 缓存，list 同步由调用方负责。

**apply 行为硬保证**：`state.value()` 与 `objectiveFunction.evaluate(externalList)` 字节级一致（同 `ScoringFunctions.penaltyXxx` 调用同 scale 舍入），由 `IncrementalPenaltyStateTest` 4 个测试验证（initial + random neighbors 200 步 + applyRevert round-trip + 5 seed 各 200 步）。

**swapTwo 处理**：`NeighborOperator.swapTwo` 一次改变两个 list 位置（left + right）。`findAllChangedIndices` 返回所有 changed indices 列表，AnnealingOptimizer 循环 apply 多次，delta 累加，accept 后 set 多个位置。`annealingSteps++` 仍每次内循环一次（不管多 idx）。

**`decrementRoomCount` 特殊**：永远保留 active 教室作为 0 占位（与 classDayCounts 等 delete-on-zero 行为不同）。原因：`penaltyClassroomUtilization` 的 avg = totalItems / size 与 avg² 依赖 size，未使用教室缺失会偏大 penalty 偏小。这是真 bug，对拍测试 step 1 失败时定位到的。

**`decrementAggregates` 对 teacherDayStarts 的空 list 清理**：移除 startPeriod 后如果 list 空，删 (teacher, day) entry；如果 inner map 空，删 teacher entry。**原因**：`penaltyContinuous` 遍历 dayItems.values() 时空 list 也算一个 sample 拉低均值（sampleCount 偏大）。也是真 bug，对拍测试 step 1 失败时定位到的。

### 测试现状

| 测试套件 | 状态 | 说明 |
|---|---|---|
| `IncrementalPenaltyStateTest` | **4/4 PASS** | 行为硬保证 |
| `AnnealingOptimizerTest` | **5/5 PASS** | 含 sameSeedSameDataProducesIdenticalAssignments（**但只用 incremental 路径跑两次，证实 incremental 自身可复现，未与全量路径对比**） |
| `mvn test` 全量 | **279/0/0，1 skipped**（gated benchmark） | BUILD SUCCESS |
| `V8BenchmarkComparisonTest` | **FAIL** | 端到端在真 MySQL 上跑，实测 incremental 大规模分数降 |

---

## 2. 今晚遇到的硬问题（明天定位用）

### 实测数据对比

| 规模 | 存档 V8 同权重分 | 增量后 V8 同权重分 | 增量后退火步数 | 增量后引擎ms |
|---|---|---|---|---|
| 小(30) | 94.97 | 94.74 | 5157 | 10428 |
| 中(120) | 89.66 | 84.38 | 3846 | 11037 |
| 大(300) | 87.70 | 87.54 | 71 | 11082 |

**关键现象**：
- 小/中分数下降（-0.2 / -5 分），大规模几乎不变（87.70 vs 87.54，偶然在内）
- **退火步数从预期 1000+ 降到大规模 71 步**——远低于理论上限（302 levels × 1500 = 450k）
- 引擎耗时 10-11s（退火 10s 预算跑满，1s 是 estimateInitialTemperature/load）

### 核心怀疑

**incremental 路径每步 ~140ms**，导致 10s 预算内主循环只跑 71 步。退火**没收敛到原解**。

**为什么 incremental 比预期慢？** 推理过程：

1. `ScoringFunctions.penaltyVariance` 内部用 `dayCounts.values().stream().mapToLong(...).average()` 对每个 owner 建 2 个 stream。80 teachers × 2 = 160 stream 创建/步。每次 stream 创建 + pipeline 启动开销大。
2. 全量 `objectiveFunction.evaluate(items)` 也用同样的 `ScoringFunctions.penaltyXxx`，**同成本**。
3. 但全量路径上 benchmark 跑 1000+ 步（之前测 10s 内完成），incremental 只 71 步。**差 14×**。

**为什么 incremental 退火步数远少于全量？** 三个可能：

- **(A) 140ms/步里大头是 estimateInitialTemperature**：它调 100 次 `objectiveFunction.evaluate`（全量）算 delta，**没有时间预算**（line 101-117 循环 100 次，无 elapsedMillis 检查）。100 次 × 140ms = 14s。**但 engine solve 实测 10-11s**——T0 估计不可能跑 100 次。
- **(B) Incremental 路径有某步比全量 evaluate 慢**：可能 `teacherDayStartsAsItemsFromCurrentMap` 重建 items list 调 `buildSyntheticItem` 创建 N 个 SchedulePlanItem 对象。但这个数量 = unique_teacher_day ≈ 400（小数据少），不该慢。
- **(C) AnnealingOptimizer 主循环开销**：内循环的 `findAllChangedIndices` 是 O(n²)（双层循环 for i × for j）。n=4（小数据）或 n=300（大数据）。n=300² = 90k 比较/步 × 70 步 = 6.3M 比较——可能。但**全量路径的 NeighborOperator.next 也调 isFeasible（O(items²)）**——量级同。

**最大怀疑 (A)**：estimateInitialTemperature 跑 100 次 evaluate，incremental 状态 + 全量 evaluate 混用导致某种 regression。但 incremental 自身每步没 evaluate 调用，理论上应当比全量快或持平。

### 其他可能（明天排查时一并验证）

- `findAllChangedIndices` 在 swapTwo 失败后还会被继续 `continue`？实际是 `if (changedIndices.isEmpty()) continue;`——空列表才 continue。
- AnnealingOptimizer 主循环 `combinedDelta = sum of per-step deltas`，多次 apply 累加；如果中间状态不准，**累加结果与全量 evaluate 单次算的 delta 不等**（即 accept 决策错误）。**但 incremental 4/4 测试已保证 state 与 evaluate 字节级一致**，sum = 最终 - 最初 = 全量 delta。逻辑上 OK。
- 退火 5/5 测试 `annealingOutputDoesNotWorsenObjectiveAndKeepsHardConstraints` 用小数据（4 task）——incremental 退火 71 步在小数据上跑出 best，但**与全量退火同一 feasible 出发可能不同 best**（incremental 路径首次被测到）。`sameSeedSameDataProducesIdenticalAssignments` 两次都走 incremental 路径，**两次一致**但**未与全量对比**。

---

## 3. 明天接手的具体方向（按推荐顺序）

### 方向 1：定位 140ms/步真实成本（必须先做）

加 timing 诊断：
1. 在 `AnnealingOptimizer.optimize` 主循环每步前 `System.nanoTime()` 测时
2. 在 `IncrementalPenaltyState.recomputePenalties` 入口/出口测时
3. 在每个 `ScoringFunctions.penaltyXxx` 调前后测时
4. 在 `findAllChangedIndices` 入口测时
5. 跑 benchmark 看 timing 分布

预期：找出 140ms/步的真实瓶颈（是 `penaltyVariance` stream？是 estimateInitialTemperature？是 findAllChangedIndices？）

### 方向 2：基于方向 1 结果优化

- 若 `penaltyVariance` stream 慢：改 for 循环实现（5 行改 50 行提速）
- 若 `findAllChangedIndices` 慢：改用 map<Long, Integer> 单遍找 changed（O(n) 而非 O(n²)）
- 若 estimateInitialTemperature 慢：加时间预算检查
- 若 incremental 重建慢：合并 `teacherDayStartsAsItemsFromCurrentMap` 进 recomputePenalties 减少重复

### 方向 3（如果方向 1/2 修不完）：回退 incremental

`AnnealingOptimizer.optimize` 改回全量 `objectiveFunction.evaluate(neighbor)` 路径，删除 `IncrementalPenaltyState`。删除 `AnnealingOptimizerTest.sameSeedSameDataProducesIdenticalAssignments` 之外的 incremental 相关测试。

**回退是 1 个 git revert**（无 commit 当前），所有代码已改但都在工作树未提交。

### 方向 4：可能直接重写

如果 incremental 思路有根本问题（e.g. `ScoringFunctions` 本身设计为一次性算，不适合增量），可以重新设计 IncrementalPenaltyState：
- 不重算 6 个 penalty，只重算受影响的几个（CONTINUOUS 除外）
- 或者：只在 accept 时调一次 evaluate，拒绝时跳过（只 70% 步真正算 penalty）

---

## 4. 关键文件路径与坐标

| 内容 | 路径 |
|---|---|
| IncrementalPenaltyState 实现 | `backend/src/main/java/com/paike/scheduler/engine/optimize/IncrementalPenaltyState.java` |
| ObjectiveFunction（增量复用点） | `backend/.../optimize/ObjectiveFunction.java` line 52-77（`assignmentToItem` static）、line 78-83（`weightFor`） |
| AnnealingOptimizer 主循环 | `backend/.../optimize/AnnealingOptimizer.java` line 38-106（`optimize`）、line 169-203（`findAllChangedIndices`） |
| AnnealingOptimizer 旧全量路径（参考） | 同文件 line 154-167 `estimateInitialTemperature`（仍用全量） |
| 对拍测试 | `backend/src/test/java/com/paike/scheduler/engine/optimize/IncrementalPenaltyStateTest.java` |
| 退火行为测试 | `backend/src/test/java/com/paike/scheduler/engine/optimize/AnnealingOptimizerTest.java` |
| Benchmark（带 annealingSteps 列） | `backend/src/test/java/com/paike/scheduler/service/V8BenchmarkComparisonTest.java` |
| 今晚 benchmark 原始输出 | `reports/v8-incremental-benchmark-raw.txt` |
| perftuning 对比基准 | `reports/v8-perf-tuning-benchmark-raw.txt` 与 `reports/v8-perf-tuning-benchmark-analysis.md` |

## 5. 重新跑这些命令可复现

```bash
# 全量测试
$env:DB_USERNAME="root"; $env:DB_PASSWORD="123456"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
cd D:\paike\backend
mvn test

# Benchmark（需 MySQL 跑 5+ 分钟）
mvn -Dtest=V8BenchmarkComparisonTest -Dv8.benchmark=true test

# 单跑 incremental 对拍
mvn -Dtest=IncrementalPenaltyStateTest test

# 单跑退火行为测试
mvn -Dtest=AnnealingOptimizerTest test
```

## 6. 提交纪律

- **未 commit 任何改动**（包含 incremental 路径所有修改 + benchmark 改 annealingSteps 输出）
- 今晚所有工作在工作树
- 明天接手时：`git status` 会看到 4 个文件 modified，1 个新文件（IncrementalPenaltyState.java）
- 决定方向 1/2/3/4 后，逐文件 commit
- `--no-ff` 合 main（如果方向 3 回退 → revert + 1 commit；如果方向 1/2 修完 → 多个 commit + merge）
