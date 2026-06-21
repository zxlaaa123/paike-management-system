# V10 Stage 4 记录：V8 引擎与自动生成

## 目标

让 V8 引擎（内存模型 + 回溯求解 + 模拟退火）支持连续周段：
自动排课生成的候选、冲突检测、落库都按 V10 周段语义判定。

核心裁决（V10_02 阶段 4）：
- `Assignment` 不加周段字段，周段属于 task
- `InMemoryConflictDetector` 在同物理时段资源列表中做 `WeekPatternSupport.overlap`
- 保留 V9 的 ODD/EVEN 逻辑 slot 翻倍机制，但保证 `ALL 1-8` 与 `ALL 9-16` 不被 false positive

## 改动清单

### 1. EngineTask（引擎任务模型）

`EngineTask` record 加两个字段：
- `startWeek`：连续周段起始周（闭区间，默认1）
- `endWeek`：连续周段结束周（闭区间，默认20）

### 2. EngineContextLoader（数据装载）

`load()` 方法构建 EngineTask 时，从 `TeachingTask.startWeek/endWeek` 透传：
- null 用 `WeekPatternSupport.normalizeStartWeek/normalizeEndWeek` 归一化为默认 1/20
- 与 V9 数据等价（V9 数据 startWeek/endWeek 均默认 1-20）

### 3. InMemoryConflictDetector（内存冲突检测器）

`check` 方法第 7-9 条 per-record 迭代（教师/班级/教室冲突），从原来的纯 `teacherIndex/classIndex/roomIdx` 相等判定，升级为相等 **且** `WeekPatternSupport.overlap` 判定：

```java
if (existingTask.teacherIndex() == teacherIdx
        && WeekPatternSupport.overlap(task.weekType(), task.startWeek(), task.endWeek(),
                existingTask.weekType(), existingTask.startWeek(), existingTask.endWeek())) {
    return "TEACHER_CONFLICT";
}
```

这是本阶段核心改动。V9 的 slot 物理翻倍模型保留：ODD/EVEN slot 仍隔离，ALL 仍扩散到配对 slot。但扩散后是否真冲突，由 `WeekPatternSupport.overlap` 按实际周集合判定。

效果：
- `ALL 1-8` 扩散到配对 slot 后，`ALL 9-16` 进同一 slot 不再被 false positive（周集合不相交）
- `ALL 1-8` 与 `ODD 5-12` 仍冲突（重叠自然周 5、7）

### 4. ObjectiveFunction（离线评分 → plan item）

`assignmentToItem`（包内静态）和 `toPlanItem`（私有）两个方法，从 Assignment 构造 SchedulePlanItem 时，补上：
- `item.setStartWeek(task.startWeek())`
- `item.setEndWeek(task.endWeek())`

保证引擎生成的 plan item 携带周段，落库后与手动排课的 plan item 字段一致。

### 5. V3ScheduleGenerateService（方案生成入口）

`generateSolverV8PlanItems` 内构造 plan item 时，`setWeekType(task.weekType())` 后补：
- `item.setStartWeek(task.startWeek())`
- `item.setEndWeek(task.endWeek())`

### 6. 未改动（保留 V9 逻辑）

- `BacktrackingSolver.listFeasibleCandidates`：slot 过滤仍按 `task.weekType` 选 ODD/EVEN slot，周段不影响 slot 选择
- `NeighborOperator.moveOne/swapTwo`：邻居生成的 slot 选择仍按 V9 翻倍逻辑
- `EngineContext.TimeSlotData`：翻倍 slot 结构不变
- `AutoScheduleService.applyWeekTypeOverlapFilter`：V9 三值逻辑暂不动（自动排课清空/统计路径，与引擎生成不同）

## 测试补充

### InMemoryConflictDetectorWeekRangeTest（新增，5 用例）

| 用例 | A | B | 预期 |
|---|---|---|---|
| `allDisjointWeekRangeNoConflict` | ALL 1-8 | ALL 9-16 同 slot | 不冲突 |
| `allOverlappingWithOddConflicts` | ALL 1-8 | ODD 5-12 同 slot | TEACHER_CONFLICT |
| `oddEvenDisjointWeekRangeNoConflict` | ODD 1-8 | EVEN 8-12 配对 slot | 不冲突 |
| `oddOverlappingWithOddConflicts` | ODD 1-9 | ODD 8-12 同 slot | TEACHER_CONFLICT |
| `allSpreadToPairedSlotDisjointNoConflict` | ALL 1-8 扩散 | ODD 9-16 进配对 slot | 不冲突 |

### 现有测试适配

23 处 `new EngineTask(...)` 构造调用补 `startWeek/endWeek` 参数（默认 1/20），涉及 6 个测试文件：
- `AnnealingOptimizerTest` / `V3ScheduleGenerateServiceTest` / `ScheduleScoreServiceTest`
- `BacktrackingSolverTest` / `InMemoryConflictDetectorWeekTypeTest` / `InMemoryConflictDetectorTest`

## 验证

### 后端

命令：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='123456'
$env:JWT_SECRET='dev_local_secret_please_change_32_chars_minimum'
cd D:\paike\backend
mvn -q "-Dtest=InMemoryConflictDetectorWeekRangeTest,InMemoryConflictDetectorWeekTypeTest,InMemoryConflictDetectorTest,BacktrackingSolverTest,AnnealingOptimizerTest,V9WeekTypeBenchmarkTest,ConflictDetectorPairTest,V3ScheduleGenerateServiceTest,ScheduleScoreServiceTest,IncrementalPenaltyStateTest" test
```

结果：全部通过（BUILD SUCCESS）。

- V10 周段新测（5 条）全过
- V9 引擎回归（`InMemoryConflictDetectorWeekTypeTest` / `V9WeekTypeBenchmarkTest` / `BacktrackingSolverTest` / `AnnealingOptimizerTest`）无回退
- DB 对拍（`ConflictDetectorPairTest` 25400 次比较）全过
- 评分链（`ScheduleScoreServiceTest` / `IncrementalPenaltyStateTest`）无回退

## 边界

本阶段未做（留给后续阶段）：

- 评分链 daily limit 按实际自然周展开计数（阶段 5）
- `ScheduleMapper.selectDailyConflictCounts` SQL 周段精确过滤（阶段 5）
- `ObjectiveFunction.penalties` 的 β 评分按周段权重计数（阶段 5，当前仍用 V9 `countableWeekTypes` 三值展开）
- `AutoScheduleService.applyWeekTypeOverlapFilter` V9 三值逻辑升级（阶段 5 或独立小改）
- `EngineWeekRangeIntegrationTest` 端到端集成测试（阶段 7 总回归补）

## 完成定义核对

| 完成定义 | 状态 |
|---|---|
| V8 可生成混合周段任务 | ✅ EngineTask 携带 startWeek/endWeek，plan item 透传 |
| DB 版冲突检测与 InMemory Detector 对拍一致 | ✅ ConflictDetectorPairTest 25400 次全过 |
| 同 seed 可复现 | ✅ 未改随机种子/求解器流程 |
| ALL 1-8 与 ALL 9-16 不被 false positive | ✅ 红线测试覆盖 |

下一阶段应进入 `V10_02_开发阶段计划.md` 的阶段 5：评分链。
