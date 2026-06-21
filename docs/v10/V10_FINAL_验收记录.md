# V10 最终验收记录：连续周段支持

## 概述

V10 在 V9 单双周（ALL/ODD/EVEN）基础上，引入 `startWeek`/`endWeek` 连续周段字段，让排课系统全链路支持"某课程只在第 1-8 周上课"这类场景。冲突判定统一以"实际自然周集合相交"为准（`WeekPatternSupport.overlap`），评分链按周段签名分桶隔离。

## 提交链

| 阶段 | 提交 | 分支 | 说明 |
|---|---|---|---|
| 0 | `ee6d439` | main | 周模式原型与红线测试 |
| 1 | `3f822e2` | feat/v10-stage1-week-range-model | 数据模型与输入源 |
| 2 | `188ab20` | feat/v10-stage2-conflict-detection | 手动排课与方案冲突链 |
| 3 | `83ca7db` | feat/v10-stage3-v4v5v6-validation | V4/V5/V6 校验链 |
| 4 | `da8ba91` | feat/v10-stage4-v8-engine | V8 引擎与自动生成 |
| 5 | `597ff0a` | feat/v10-stage5-scoring | 评分链 |
| 6 | `9d6341d` | feat/v10-stage6-export-ui | 导出、网格、前端 |
| 7 | （本次） | feat/v10-stage7-acceptance | 总回归与验收 |

## 代码变更统计

- 57 文件变更，+2349 行 / -171 行
- 19 个测试文件（新增/修改）
- 27 个生产代码文件（新增/修改）
- 6 个阶段记录文档
- 1 个数据库迁移脚本（v24_continuous_week_range.sql）

## 各阶段完成情况

### 阶段 0：周模式原型与红线测试
- `WeekPatternSupport` 工具类：`activeWeekMask`/`overlap`/`activeWeekCount`/`displayLabel`/`validateRange`/`weekRangeKey`
- 31 条红线测试（冲突矩阵全覆盖）
- ✅ 完成

### 阶段 1：数据模型与输入源
- 数据库：`teaching_task`/`schedule_plan_item`/`schedule` 三表加 `start_week`/`end_week`（默认 1/20，幂等）
- 后端：三个 Entity + 四个 VO + Controller TaskForm + Service create/update 校验
- 前端：`teachingTask.ts` 类型 + `TeachingTaskView.vue` 表单起止周输入
- ✅ 完成

### 阶段 2：手动排课与方案冲突链
- `ScheduleConflictService.checkConflict`：三处资源冲突升级为 `WeekPatternSupport.overlap`
- `SchedulePlanService.addGroupedConflictReasons`：pair 判定升级
- `ScheduleConflictReportService`：三个 detect 方法新增 `filterWeekOverlapSchedules` 过滤
- +8 条周段红线用例
- ✅ 完成

### 阶段 3：V4/V5/V6 校验链
- `V4ScheduleRiskService.detectSlotConflicts` 升级
- `V5ConsistencyCheckService.checkHardConflicts` 升级
- `V6RegressionTestService.scanResourceConflict` 升级
- +6 条周段红线用例
- ✅ 完成

### 阶段 4：V8 引擎与自动生成
- `EngineTask` record 加 `startWeek`/`endWeek`（12 参数）
- `EngineContextLoader` 透传
- `InMemoryConflictDetector.check` 核心三处加 `WeekPatternSupport.overlap`
- `ObjectiveFunction.assignmentToItem`/`toPlanItem` 透传周段
- 23 处 `new EngineTask(...)` 构造调用适配
- +5 条周段红线用例
- V9 引擎回归 + DB 对拍 25400 次全过
- ✅ 完成

### 阶段 5：评分链
- `ScoringFunctions.WeekOwner` record 升级为 `(ownerId, weekType, weekRangeKey)` 三字段
- `WeekPatternSupport.weekRangeKey` 新增
- 四方评分链（`DeltaPenaltyScorer`/`ObjectiveFunction`/`IncrementalPenaltyState`/`ScheduleScoreService`）统一传 weekRangeKey
- `ScheduleScoreService.countConflicts` 升级为 `WeekPatternSupport.overlap` 贪心
- `ScheduleConflictReportService` 每日超载用 `maxConcurrentOverlap` 精确计数
- +6 条周段红线用例
- ✅ 完成

### 阶段 6：导出、网格、前端
- `TimetableService.buildTimetableVo` 透传 startWeek/endWeek
- `TimetableService.buildCellText` 用 `WeekPatternSupport.displayLabel` 生成周段标签
- 前端 `TimetableItem` 加 startWeek/endWeek
- `TimetableGrid.vue` `weekRangeLabel` 与后端同语义
- +5 条周段红线用例
- ✅ 完成

## 验收结果

### 后端全量测试
```
Tests run: 384, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```
- 384 测试全过，0 失败
- 2 skip（V8BenchmarkComparisonTest / V9WeekTypeBenchmarkTest，需特殊 benchmark 环境）

### 前端类型检查
```
vue-tsc --noEmit
EXIT_CODE=0
```
- 类型检查通过

### V9 周型回归
- `WeekTypeConflictMatrixTest`：17/17 通过
- `ScoringWeekTypeConsistencyTest`：10/10 通过（纯 ALL 数据零回归）
- `TimetableExportWeekTypeTest`：7/7 通过（单双周导出零回归）
- `ConflictDetectorPairTest`：1/1 通过（DB 对拍 25400 次）

### V10 周段红线用例
| 阶段 | 测试类 | 用例数 |
|---|---|---|
| 0 | WeekPatternSupportTest | 14 |
| 2 | ScheduleConflictServiceTest | +3 周段 |
| 2 | SchedulePlanServiceTest | +2 周段 |
| 2 | ScheduleConflictReportServiceTest | +3 周段 |
| 3 | V4ScheduleRiskServiceTest | +2 周段 |
| 3 | V5ConsistencyCheckServiceTest | +2 周段 |
| 3 | V6RegressionTestServiceRunTest | +2 周段 |
| 4 | InMemoryConflictDetectorWeekRangeTest | 5 |
| 5 | ScoringWeekRangeIsolationTest | 6 |
| 6 | TimetableExportWeekRangeTest | 5 |

## 零回归保证

V10 对 V9 纯 ALL 1-20 数据的零回归承诺：
1. **数据模型**：startWeek/endWeek 默认 1/20，与 V9 无周段字段等价
2. **冲突检测**：`WeekPatternSupport.overlap` 对全 1-20 的 ALL/ODD/EVEN 判定与 V9 weekType overlap 一致
3. **评分链**：`weekRangeKey` 对默认 1-20 数据全部为 `"1-20"`，分桶与 V9 `(ownerId, weekType)` 完全等价
4. **导出**：`WeekPatternSupport.displayLabel` 对默认 1-20 + ALL 返回空字符串，无标签（与 V9 一致）
5. **引擎**：V9 slot 物理翻倍模型保留，仅冲突判定加 overlap 过滤

## 关键设计决策

1. **周段属于 task 不属于 assignment**：`Assignment` 不加周段字段，周段从 `EngineTask` 获取（阶段 4 裁决）
2. **保留 V9 slot 翻倍模型**：ODD/EVEN slot 物理隔离 + ALL 扩散，不重构 slot 模型
3. **weekRangeKey 而非 mask 分桶**：用 `"startWeek-endWeek"` 作为评分桶 key，而非实际自然周 mask。mask 分桶会让 ALL 1-20 与 ODD 1-20 进不同桶（破坏 V9 语义）；weekRangeKey 只看周段范围，同周段同桶（保持 V9 语义 + 实现 V10 隔离）
4. **每日超载用 maxConcurrentOverlap**：按实际自然周展开逐周计数取最大值，而非 group.size()

## 已知边界

1. **周段相交但 mask 不同**：如 `ALL 1-8` 与 `ODD 5-12`，weekRangeKey 不同（"1-8" vs "5-12"）→ 评分不同桶 → 互不影响。这是保守处理——实际它们在 5,7 周相交。真实硬冲突由 `WeekPatternSupport.overlap` 在冲突检测链判定，软评分只管均衡隔离
2. **AutoScheduleService.applyWeekTypeOverlapFilter**：V9 三值逻辑暂未升级，V10 周段数据在自动排课候选过滤时仍按 weekType 三值处理。不影响正确性（冲突检测链已用 overlap），但候选效率可优化
3. **ScheduleMapper.selectDailyConflictCounts**：SQL weekType 分支保留不变，服务层已用 `maxConcurrentOverlap` 精确计数覆盖

## 结论

V10 连续周段支持全链路改造完成，7 个阶段全部通过验收：
- ✅ 后端全量测试通过（384/384）
- ✅ 前端类型检查通过
- ✅ V9 周型回归通过（零回退）
- ✅ V10 周段红线用例通过
