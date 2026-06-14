# gpt55-周次单双周-严审裁决

## 1. 事实核验

| 断言 | 判断 | 证据 |
|---|---|---|
| `schedule_plan_item` 有 `week_type`，且 `uk_plan_task_slot` 不含它 | 属实 | [v3_schedule_plan.sql:45](<D:\paike\backend\src\main\resources\db\v3_schedule_plan.sql:45>), [v3_schedule_plan.sql:53](<D:\paike\backend\src\main\resources\db\v3_schedule_plan.sql:53>) |
| `SchedulePlanItem` 有 `weekType`，但后端对 `weekType/week_type` 零读写 | 部分属实 | [SchedulePlanItem.java:42](<D:\paike\backend\src\main\java\com\paike\scheduler\entity\SchedulePlanItem.java:42>), [V3ScheduleGenerateService.java:504](<D:\paike\backend\src\main\java\com\paike\scheduler\service\V3ScheduleGenerateService.java:504>), [ObjectiveFunction.java:73](<D:\paike\backend\src\main\java\com\paike\scheduler\engine\optimize\ObjectiveFunction.java:73>), [V5SimulationService.java:893](<D:\paike\backend\src\main\java\com\paike\scheduler\service\V5SimulationService.java:893>), [SchedulePlanService.java:724](<D:\paike\backend\src\main\java\com\paike\scheduler\service\SchedulePlanService.java:724>) |
| `schedule` 正式课表无 `week_type` | 属实 | [schema.sql:118-129](<D:\paike\backend\src\main\resources\db\schema.sql:118>) |
| 系统有三套冲突检测：DB 版、V3 版、V8 内存版 | 属实 | [ScheduleConflictService.java:54-149](<D:\paike\backend\src\main\java\com\paike\scheduler\service\ScheduleConflictService.java:54>), [V3ScheduleGenerateService.java:416-431](<D:\paike\backend\src\main\java\com\paike\scheduler\service\V3ScheduleGenerateService.java:416>), [InMemoryConflictDetector.java:60-146](<D:\paike\backend\src\main\java\com\paike\scheduler\engine\conflict\InMemoryConflictDetector.java:60>) |
| V8 `TimeSlotData` 只有 `dayOfWeek` / `periodNo`，无周次概念 | 属实 | [EngineContext.java:30](<D:\paike\backend\src\main\java\com\paike\scheduler\engine\model\EngineContext.java:30>) |

### 结论

文档对“`week_type` 语义未激活”的方向判断基本对，但“全链路从未读写”不成立。现状更准确的表述是：`weekType` 被大量透传/默认写成 `ALL`，但没有作为分歧条件进入任何核心业务决策。

## 2. 影响面评估

1. 影响面分析不够全。
   `ScoringFunctions`、`ScheduleScoreService`、`ObjectiveFunction`、`IncrementalPenaltyState` 都按 `weekday + startPeriod` 聚合，单双周一旦共槽，会把两类记录算成同一格。证据见 [ScoringFunctions.java:57-107](<D:\paike\backend\src\main\java\com\paike\scheduler\service\scheduling\ScoringFunctions.java:57>)、[ScheduleScoreService.java:198-228](<D:\paike\backend\src\main\java\com\paike\scheduler\service\ScheduleScoreService.java:198>)、[ObjectiveFunction.java:58-99](<D:\paike\backend\src\main\java\com\paike\scheduler\engine\optimize\ObjectiveFunction.java:58>)、[IncrementalPenaltyState.java:19-27](<D:\paike\backend\src\main\java\com\paike\scheduler\engine\optimize\IncrementalPenaltyState.java:19>)。

2. `TimetableService` 也是硬耦合点。
   导出网格按 `day + period` 做 key，同格只能留一条，单双周会互相覆盖。证据见 [TimetableService.java:309-326](<D:\paike\backend\src\main\java\com\paike\scheduler\service\TimetableService.java:309>)。

3. V5 / V4 / 报表链路也受影响，文档漏了。
   不是只有三套“核心冲突检测”要改，下面这些同样按日/节或 timeSlot 聚合：  
   `SchedulePlanService.refreshPlanConflictState`、`V4ScheduleRiskService`、`V5RuleEvaluationService`、`V5ConsistencyCheckService`、`V5CandidatePositionService`、`ScheduleConflictReportService`、`ScheduleScoreReportService`、`AutoScheduleService`。  
   证据见 [SchedulePlanService.java:226-299](<D:\paike\backend\src\main\java\com\paike\scheduler\service\SchedulePlanService.java:226>)、[V4ScheduleRiskService.java:154-170](<D:\paike\backend\src\main\java\com\paike\scheduler\service\V4ScheduleRiskService.java:154>)、[V5RuleEvaluationService.java:152-185](<D:\paike\backend\src\main\java\com\paike\scheduler\service\V5RuleEvaluationService.java:152>)、[V5ConsistencyCheckService.java:369-425](<D:\paike\backend\src\main\java\com\paike\scheduler\service\V5ConsistencyCheckService.java:369>)、[V5CandidatePositionService.java:305-365](<D:\paike\backend\src\main\java\com\paike\scheduler\service\V5CandidatePositionService.java:305>)、[ScheduleConflictReportService.java:179-225](<D:\paike\backend\src\main\java\com\paike\scheduler\service\ScheduleConflictReportService.java:179>)、[ScheduleScoreReportService.java:202-225](<D:\paike\backend\src\main\java\com\paike\scheduler\service\ScheduleScoreReportService.java:202>)、[AutoScheduleService.java:260-305](<D:\paike\backend\src\main\java\com\paike\scheduler\service\AutoScheduleService.java:260>)。

4. 现有任务模型没有 week 语义来源。
   `TeachingTask` 只有 `weeklyHours/needContinuous/status`，没有 `weekType`；前端 task API 也没有这个字段。也就是说，`weekType` 目前只是“输出时存在”，不是“输入时存在”。证据见 [TeachingTask.java:21-35](<D:\paike\backend\src\main\java\com\paike\scheduler\entity\TeachingTask.java:21>)、[schema.sql:72-84](<D:\paike\backend\src\main\resources\db\schema.sql:72>)、[frontend/src/api/teachingTask.ts:22-30](<D:\paike\frontend\src\api\teachingTask.ts:22>)。

5. 正式课表应用链也没闭环。
   `SchedulePlanService.applyPlan` 仍把 plan item 映射到 `time_slot_id`，正式课表唯一键也仍按 `semester_id + time_slot_id + entity + active_key` 控制。没有 `schedule.week_type` 之前，单双周正式落库必然丢语义或互相冲突。证据见 [SchedulePlanService.java:378-436](<D:\paike\backend\src\main\java\com\paike\scheduler\service\SchedulePlanService.java:378>)、[v22_schedule_semester_unique.sql:41-67](<D:\paike\backend\src\main\resources\db\v22_schedule_semester_unique.sql:41>)。

### 工作量

`2-3` 周偏乐观。  
如果只做“计划项 + V8 + 应用链”还勉强，但只要把评分、导出、V4/V5 试算、校验、报表一起算进“可交付”，更接近 `3-5` 周。

### 风险

R1-R6 是底稿，但不够全。至少还缺这几类：

- week 语义的**输入源风险**：没定义 weekType 从哪来，字段加了也只是展示层。
- 导出/看板的**静默覆盖风险**：同一 day/period 的 odd/even 会互相顶掉。
- V5 试算 / 一致性检查 / 候选位置 / 风险报告的**假冲突风险**。
- 正式课表应用的**语义丢失风险**：`schedule` 表和 unique key 还没为 weekType 做闭环。

## 3. 裁决

1. 是否建议启动 V9？
   建议启动，但只做窄版，不要把 `B` 一起吞进去。

2. 选 A / B / C？
   选 `A`。`B` 复杂度明显更高，应该留后。

3. V8 引擎是否一并改？
   必须改。否则只是把 weekType 挂在 plan item 上，自动排课本身还是单周模型。

4. 是否认同“`A + V8 一并改 + 独立 V9 流程`”？
   方向认同，完备性不认同。原文漏了输入源、导出、V4/V5 评估链、正式课表闭环这几块，不能按当前边界直接开工。

5. 替代路径
   `V9-A` 分两层推进：
   - 先补 weekType 的唯一来源、落库链、V8 引擎、正式课表应用。
   - 再补评分 / 导出 / 试算 / 一致性检查 / 风险报表的 week 兼容。
   `B` 以后再做。

