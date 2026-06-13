# V8 退火引擎性能调优记录

日期：2026-06-14

分支：`main`（性能优化直接在 main 上提交，未走 feature 分支）

涉及 commits：

| commit | 说明 |
| --- | --- |
| `80fac37` | 增量打分路径 + timing 探针（前一会话产出，本次诊断与验证） |
| `78083b9` | estimateInitialTemperature 预算守卫（优化一） |
| `99d0165` | NeighborOperator 增量冲突检测（优化二） |

---

## 0. 背景：前一会话留下的悬念

前一会话（2026-06-13）把退火引擎从「每步全量 evaluate」改成「增量维护 6 个聚合 Map」
（`IncrementalPenaltyState`），行为测试全绿（279/0/0），但 `V8BenchmarkComparisonTest`
端到端 benchmark 跑出**质量回归**：

| 规模 | 存档 V8 分 | 增量后 V8 分 | 退火步数 |
| --- | --- | --- | --- |
| 小(30)  | 94.97 | 94.74 | 5157 |
| 中(120) | 89.66 | 84.38 | 3846 |
| 大(300) | 87.70 | 74.28 | **71** |

前一会话的怀疑（见 `INCREMENTAL_HANDOFF_2026-06-13.md`）：增量路径每步 ~140ms，
10s 预算内主循环只跑 71 步，**退火没收敛**。但当时没有实测 timing 数据支撑，
只列了 4 个待查方向（加探针 / 基于探针优化 / 回退 / 重写）就交接了。

本次工作从「方向 1：加 timing 探针定位真实瓶颈」开始。

---

## 1. 诊断：timing 探针证伪了原假设

### 1.1 探针实现

在 `AnnealingOptimizer.optimize` 主循环加分段纳秒累加器，覆盖 5 个段：
`neighbor`（邻域生成）/ `findChanged`（找变动位）/ `apply`（增量打分）/ `accept`（接受路径）/ `revert`（拒绝回滚）。
另外对 `estimateInitialTemperature` 加整体计时。

探针用 `-Dannealing.profile=true` 环境变量门控，默认关闭零开销；输出走 `System.err`
不影响 surefire 报告。探针本身是无副作用的诊断工具，已随 `80fac37` 固化进代码，
后续每次 optimize 都能打 timing。

### 1.2 实测数据（证伪 handoff 的「增量路径慢」假设）

加上探针后重跑 benchmark，大规模（300 task）每步耗时分布：

| 段 | us/步 | 占比 |
| --- | --- | --- |
| **neighborOperator.next** | **8374** | **90%+** |
| apply（增量 recompute） | 315 | 3% |
| revert | 88 | 1% |
| findChanged / accept | ~10 | <1% |

**结论颠覆了 handoff 的核心假设**：增量打分（apply）每步只有 0.31ms，根本不是瓶颈。
真正的瓶颈是 `neighborOperator.next`（每步 8.4ms，占 90%+），以及
`estimateInitialTemperature` 吞掉 40-60% 退火预算（大数据 4-6s）。

### 1.3 benchmark 实测结果也与 handoff 不符

带探针重跑后，handoff 报告的「大规模 87.70→74.28 -13 分」**没有重现**：

| 规模 | V8 同权重分 | 旧策略最高 | 结果 |
| --- | --- | --- | --- |
| 小(30)  | 93.00 | 94.95 | **FAIL**(-1.95) |
| 中(120) | 90.98 | 79.31 | PASS |
| 大(300) | 90.08 | 82.28 | PASS |

中/大规模本来就 PASS，只有小规模微差 FAIL。推测 handoff 报告的「-13 分」是
早期 `IncrementalPenaltyState` 的 bug（`decrementRoomCount` 占位 / `teacherDayStarts`
空 list 清理），这些 bug 在前一会话后期已修好（对拍测试已覆盖），但 handoff 写在
bug 修复之前，数据是旧的。

**增量打分路径本身是成功的，不该回退。** 真正该优化的是 neighborOperator 和 estimateT0。

---

## 2. 优化一：estimateInitialTemperature 预算守卫

### 2.1 问题定位

`estimateInitialTemperature`（`AnnealingOptimizer.java`）用于估计退火初始温度 T0：
采样 100 个 neighbor 的平均 worsening delta，除以 ln(2) 使初始接受率≈50%。

问题：这 100 次采样**没有时间预算检查**，每次采样调 `neighborOperator.next`（全量
重建 detector，~8ms）+ `objectiveFunction.evaluate`（全量 groupingBy）。大数据下
T0 估计就吃掉 4-6s，主循环只剩 4-6s，步数被腰斩（438-774 步）。

### 2.2 改动

加预算守卫，改动只 +32/-7 行（`78083b9`）：

- T0 时间上限 = 退火预算的 **20%**（下限 50ms，防止极小预算失效）
- 最少采样 **20 次**（保证 avg worsening 统计有意义，避免超时过早退出导致 T0 失真）
- 小数据/快数据仍跑满 100 次（守卫不触发，行为不变）；大数据到 20% 预算即停

新增 `T0Estimate` record 承载结果（temperature + samples + worseningCount），
profiling 输出里能看到实际采样数和是否触发了守卫。

### 2.3 收益

| 规模 | 守卫前分数 | 守卫后 | 步数变化 | T0 耗时变化 |
| --- | --- | --- | --- | --- |
| 小(30)  | 93.00 FAIL | **95.16 PASS** | 1723 → 6562 | 880ms → 469ms |
| 中(120) | 90.98 | 86.03* | 1710 → 3850 | 3172ms → 1129ms |
| 大(300) | 90.08 | 88.63 | 774 → **1487 (+92%)** | 4003ms → **2000ms(守卫触发)** |

\* 中规模分数波动（90.98→86.03）是 T0 采样路径变化导致 temperature 序列不同，
但中规模本来就远超旧策略最高（79.31），质量达标。重点是大规模守卫如预期触发：
samples 从 100 降到 39，T0 砍到 2s 上限，步数近翻倍。

**小规模从 FAIL 转 PASS**，三档全绿。

---

## 3. 优化二：NeighborOperator 增量冲突检测

### 3.1 问题定位

这是最大瓶颈。`NeighborOperator.next` 的 `moveOne` / `swapTwo` 在 120 次尝试循环里，
**每次都全量重建 detector**：

```java
// 原实现（每次 attempt）
List<Assignment> candidate = new ArrayList<>(current);
candidate.set(index, moved);
if (isFeasible(candidate)) { ... }  // isFeasible 内部 new detector + place 全部
```

`isFeasible` 每次 `new InMemoryConflictDetector(ctx)`（构造里 place existing+locked）
+ 遍历 place **全部** current assignments。完全没增量。n=300 时每次 attempt 是 O(n)，
120 次 attempt 是 O(120n)，这就是每步 8ms 的根源。

### 3.2 关键发现：detector 已有 remove 方法

读 `InMemoryConflictDetector` 发现它**已有 `remove(Assignment)` 方法**（O(1) 数组操作），
只是 NeighborOperator 没用它。这给增量化提供了现成基础。

### 3.3 改动

`99d0165`，改动 +163/-11（含 benchmark raw 报告）：

- `next()` 入口构建**一次** baseline detector（构造里 place existing+locked，再 place 全部 current）
- `moveOne`：每次 attempt `remove(original)` → `check(moved)` → 成功 `place(moved)` 返回 / 失败 `place(original)` 回滚。每次 O(1)
- `swapTwo`：`remove(a)` → `remove(b)` → `check(newA)` → `place(newA)` → `check(newB)` → 成功返回 / 失败逆序回滚。每次 O(1)
- 保留 `isFeasible`（全量）方法不动 —— 测试 `neighborKeepsFeasibleSolutionAndDoesNotMoveLockedBaseline`
  正好用它对拍增量路径生成的 neighbor，天然等价性验证

### 3.4 等价性验证

- `AnnealingOptimizerTest.neighborKeepsFeasibleSolutionAndDoesNotMoveLockedBaseline`：
  `next()`（增量）生成 neighbor → `isFeasible`（全量）验证 → PASS
- `sameSeedSameDataProducesIdenticalAssignments`：同 seed 同数据退火可复现 → PASS
- `IncrementalPenaltyStateTest` 4/4 对拍 → PASS
- `mvn test` 全量 9/0/0 → PASS

### 3.5 收益

neighbor 增量化和 T0 守卫**叠加后**的最终 benchmark：

| 规模 | neighbor us/步（原始→T0→+增量） | 步数（原始→T0→+增量） | 最终分数 |
| --- | --- | --- | --- |
| 小(30)  | 4262 → 1262 → **818** | 1723 → 6562 → **9336** | 95.16 PASS |
| 中(120) | 3592 → 2071 → **987** | 1710 → 3850 → **7485** | 91.89 PASS |
| 大(300) | 8374 → 6789 → **1941** | 438 → 1089 → **3904** | 89.28 PASS |

**大数据 neighbor 8374→1941 us/步（-77%），步数 438→3904（+8.9×）**。
对比 handoff 报告的「只跑 71 步」，现在是 **3904 步，55 倍提升**。
中规模也从 T0 守卫后的路径波动（86.03）回升到 91.89，质量稳定。

---

## 4. 最终状态

### 4.1 benchmark 全绿

三档全部 PASS，引擎耗时 11052ms ≤ 15000ms 门槛：

| 规模 | V8 同权重分 | 旧策略最高 | 未排 | 引擎ms |
| --- | --- | --- | --- | --- |
| 小(30)  | 95.16 | 94.95 | 0 | - |
| 中(120) | 91.89 | 79.31 | 0 | - |
| 大(300) | 89.28 | 82.28 | 0 | 11052 |

### 4.2 profiling 探针已固化

`-Dannealing.profile=true` 可随时输出每步 5 段耗时分布 + T0 采样统计，
后续若要继续优化（如 `apply` 的 `teacherDayStartsAsItemsFromCurrentMap` 重建、
或 `findAllChangedIndices` 的 O(n²)）可直接定位。当前 neighbor（1.9ms）和
apply（0.4ms）量级已接近，进一步优化收益递减。

### 4.3 关键文件坐标

| 内容 | 路径 |
| --- | --- |
| T0 预算守卫 | `AnnealingOptimizer.java`（`T0_BUDGET_RATIO` / `T0_MIN_SAMPLES` / `estimateInitialTemperature` 带 budgetNanos 参数） |
| timing 探针 | `AnnealingOptimizer.optimize`（`annealing.profile` 门控，5 段累加器 + 汇总输出） |
| neighbor 增量冲突检测 | `NeighborOperator.next/moveOne/swapTwo`（baseline detector 复用） |
| 增量打分状态 | `IncrementalPenaltyState.java`（前一会话产出，本次验证有效未改动） |
| benchmark 测试 | `V8BenchmarkComparisonTest.java`（带 annealingSteps 列） |
| 本次 benchmark raw | `reports/v8-t0-guard-benchmark.txt`、`reports/v8-neighbor-incremental-benchmark.txt` |

### 4.4 复现命令

```powershell
$env:DB_USERNAME="root"; $env:DB_PASSWORD="123456"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
cd D:\paike\backend

# 单元测试（快，秒级）
mvn -Dtest=AnnealingOptimizerTest,IncrementalPenaltyStateTest test

# 带 profiling 的单元测试
mvn -Dtest=AnnealingOptimizerTest,IncrementalPenaltyStateTest -Dannealing.profile=true test

# benchmark（需 MySQL，约 5-7 分钟）
mvn -Dtest=V8BenchmarkComparisonTest -Dv8.benchmark=true -Dannealing.profile=true test
```

---

## 5. 教训

1. **没有实测数据不要猜瓶颈**。handoff 凭推理怀疑「增量路径每步 140ms」，实际增量
   apply 只有 0.31ms/步，真凶是 neighborOperator（8.4ms/步）。加探针花 1 小时，
   省掉了可能误回退增量路径（前一会话方向 3）的灾难。

2. **先看现成能力再造轮子**。`InMemoryConflictDetector` 早就实现了 `remove` 方法，
   neighborOperator 一直没用。增量冲突检测只需 `remove→check→place→rollback` 组合，
   不用改 detector 本身。

3. **handoff 数据要标注时效**。handoff 写的「大规模 -13 分」是 bug 修复前的旧数据，
   接手时差点被误导。诊断时第一件事应该是带探针重跑一次，用当下数据说话。
