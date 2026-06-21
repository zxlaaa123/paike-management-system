# V10 阶段 5 记录：评分链周段支持

## 目标
让在线增量评分、离线 rescore、V8 引擎 ObjectiveFunction 三方评分口径一致，支持连续周段。

## 核心裁决

### WeekOwner 复合键升级
`ScoringFunctions.WeekOwner` record 从 `(ownerId, weekType)` 升级为 `(ownerId, weekType, weekRangeKey)`。

`weekRangeKey` = `WeekPatternSupport.weekRangeKey(weekType, startWeek, endWeek)` = `"startWeek-endWeek"`（如 `"1-8"`、`"9-16"`、`"1-20"`）。

**语义**：
- 周段相同的两条 item → 同 weekRangeKey → 同桶 → 互相影响方差评分
- 周段不同的两条 item → 不同 weekRangeKey → 不同桶 → 互不影响
- 纯 ALL 1-20 数据所有 weekRangeKey 相同（`"1-20"`）→ 与 V9 完全等价（零回归）
- ALL 1-20 展开为 ODD/EVEN 后与纯 ODD 1-20 / EVEN 1-20 同周段 → 同桶（保持 V9 语义）

### 为什么不用 mask（实际自然周集合）作为 key
mask 分桶会让 ALL 1-20（mask=0xFFFFF）与 ODD 1-20（mask=0x55555）进不同桶——但 V9 下 ALL 展开为 ODD+EVEN 后与纯 ODD 在同一 ODD 桶。mask 分桶破坏了 V9 语义，导致 `betaAggregation_oddEvenIndependent` 等回归测试失败。

weekRangeKey 分桶只看周段范围（1-20），不看 weekType 展开后的实际周集合。这保持了 V9 的"同周段同桶"语义，同时实现了 V10 的"不同周段互不影响"目标。

### 硬冲突计数升级
`ScheduleScoreService.countConflicts` 从 V9 的 weekType 分桶（ALL/ODD/EVEN 三桶独立计数）升级为 `WeekPatternSupport.overlap` 贪心判定：
- 同 (owner, day, period) 分组后，组内按 mask 降序贪心保留
- 与已保留的任意一条 overlap 即计为冲突
- 纯 ALL 1-20 数据组内全 overlap，结果仍为 size-1（零回归）
- ODD+EVEN 共槽（合法）不冲突；ALL 1-8 vs ALL 9-16 不冲突

### 每日超载精确计数
`ScheduleConflictReportService.detectTeacherDailyOverload` / `detectClassDailyOverload` 从 `group.size()` 升级为 `maxConcurrentOverlap(group)`：
- 将每条 schedule 的活跃周展开为 week 集合
- 逐周统计该组有多少条 schedule 在此周活跃
- 取最大值作为"同一周段内最大大节数"
- V9 数据（全 1-20）所有 schedule 活跃周相同，退化为 group.size()（零回归）
- 周段不相交的 schedule 不会在同一周被同时计入

## 改动清单

### 生产代码
1. `WeekPatternSupport.java`：新增 `weekRangeKey(weekType, startWeek, endWeek)` 方法
2. `ScoringFunctions.java`：`WeekOwner` record 升级为 3 字段（加 `weekRangeKey`）
3. `DeltaPenaltyScorer.java`：
   - 加 `weekOwner(ownerId, weekType, item)` 辅助方法
   - `nestedDayCountsBeta`/`nestedDayItemsBeta`/`courseDayCountsBeta` 构造 WeekOwner 时传 weekRangeKey
   - `courseDayKeyBeta` key 加 weekRangeKey 后缀
   - `variancePenaltyAfterBeta`/`continuousPenaltyAfterBeta`/`classGapPenaltyAfterBeta` candidate 构造 WeekOwner 时传 weekRangeKey
4. `ObjectiveFunction.java`：
   - 加 `weekOwner(ownerId, weekType, item)` 辅助方法
   - `nestedDayCountsBeta`/`nestedDayItemsBeta`/`courseDayCountsBeta` 构造 WeekOwner 时传 weekRangeKey
5. `IncrementalPenaltyState.java`：
   - 加 `weekOwner(ownerId, weekType, item)` 和 `courseDayKey(item, weekType)` 辅助方法
   - 所有 `new WeekOwner(x, wt)` 替换为 `weekOwner(x, wt, it)`
   - `courseDayCountsBeta` 和 `decrementAggregates`/`incrementAggregates` 的 key 用 `courseDayKey`
6. `ScheduleScoreService.java`：
   - 加 `weekOwner(ownerId, weekType, item)` 辅助方法
   - `nestedDayCountsBeta`/`nestedDayItemsBeta` 构造 WeekOwner 时传 weekRangeKey
   - `courseDayCounts` key 加 weekRangeKey 后缀
   - `countConflicts` 升级为 `WeekPatternSupport.overlap` 贪心判定
7. `ScheduleConflictReportService.java`：
   - `detectTeacherDailyOverload`/`detectClassDailyOverload` 用 `maxConcurrentOverlap` 替代 `group.size()`
   - 新增 `maxConcurrentOverlap(group)` 方法

### 测试
1. `ScoringWeekRangeIsolationTest.java`（新增）：6 条红线用例
   - `disjointWeekRanges_noVarianceInteraction`：不相交周段不互相影响方差
   - `intersectingButDifferentMask_separateBuckets`：不同周段签名进不同桶
   - `pureAllData_zeroRegression`：纯 ALL 1-20 零回归
   - `sameWeekRange_interactionPreserved`：同周段仍互相影响
   - `courseDistribution_disjointRangesNotDuplicate`：不相交周段不算课程重复
   - `pureAllData_sameBucketCountAsV9`：纯 ALL 桶数与 V9 一致
2. `ScoringWeekTypeConsistencyTest.java`：`WeekOwner` 构造适配 3 字段

## 验证结果

### 红线测试
- `ScoringWeekRangeIsolationTest`：6/6 通过
- V9 下 2 条失败（`disjointWeekRanges` penalty≠0，桶数≠4）→ 证明测试有效

### V9 回归
- `ScoringWeekTypeConsistencyTest`：10/10 通过（纯 ALL 数据零回归）
- `IncrementalPenaltyStateTest`：4/4 通过（增量与全量一致）
- `AnnealingOptimizerTest`：5/5 通过（同 seed 同结果）
- `ScheduleScoreServiceTest`：6/6 通过
- `ConflictDetectorPairTest`：1/1 通过（DB 对拍 25400 次）
- `InMemoryConflictDetectorWeekRangeTest`：5/5 通过

### 全量回归
- 379 测试全过，0 失败，2 skip（benchmark 需特殊环境）

## 完成定义核对
- ✅ 增量评分与全量评分一致（`IncrementalPenaltyStateTest` 通过）
- ✅ V8 Objective 与 ScheduleScoreService 一致（三方共用 `weekOwner` 辅助方法 + `WeekPatternSupport.weekRangeKey`）
- ✅ 旧纯 ALL 数据分数不回退（`pureAllData_zeroRegression` + `ScoringWeekTypeConsistencyTest` 通过）
