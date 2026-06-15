# V9 阶段 2 记录（评分 / 导出 / V4·V5 校验链）

日期：2026-06-14

分支：`feature/v9-stage1-data-conflict`

## 做了什么

阶段 2 处理评审发现的三条链路（R7 评分语义、R9 导出覆盖、R10 V4/V5 假冲突），让旧四策略全链路支持单双周。阶段 2 完成后 **V9.1 完整收口**（旧策略全链路支持单双周，V8 引擎仍 stub 拒绝留阶段 3）。

### 2A 评分链（β 独立计数）—— commit `0c81409`

实现 V9_00 §5 #5 裁决：聚合维度 `(owner/day)` → `(owner/day/weekType)`，单周课只计单周负荷、双周课只计双周、ALL 同时计入两者。

| 文件 | 改动 |
|---|---|
| `WeekTypeSupport.java` | 新增 `countableWeekTypes(String)` —— β 展开单一真相源（ALL→[ODD,EVEN]，ODD→[ODD]，EVEN→[EVEN]） |
| `ScoringFunctions.java` | 新增 `WeekOwner` record + `penaltyVarianceBeta` / `penaltyContinuousBeta` 重载。**不删旧签名**（引擎 ObjectiveFunction/IncrementalPenaltyState 继续用旧签名，引擎所有 item 硬编码 ALL，旧签名对纯 ALL 世界正确，阶段 3 激活） |
| `ScheduleScoreService.java` | `buildScoreContext` 聚合 key 升级为 `(ownerId, weekType)` + ALL 展开；`countConflicts` 改用 `WeekTypeSupport.overlap` 成对判定（ODD+EVEN 共槽不再误报硬冲突） |
| `DeltaPenaltyScorer.java` | 在线贪心增量 β 化：PenaltyContext 字段 + `*AfterBeta` 方法按 candidate 的 countableWeekTypes 展开到对应桶算 before/after delta |

**关键性质（零回归承诺）**：纯 ALL 数据展开后 ODD/EVEN 桶完全对称，归一化后 classBalance/teacherLoad/continuous/courseDistribution 数值与改造前完全相同。已手算验证 ScheduleScoreServiceTest fixture1（classBalance 0.0313、teacherLoad 0.0313、continuous 0.3333 全部不变），现有 baseline 测试**无需重新锁定**。

**关键设计决策**：用户确认 DeltaPenaltyScorer（在线贪心）一并纳入 β，避免在线/离线双轨漂移；硬冲突计数（countConflicts）一并修（β 与硬冲突同在 buildScoreContext）。

**新增测试 T5**（`ScoringWeekTypeConsistencyTest`，6 用例）：β 语义断言（ODD 只进 ODD 桶、EVEN 只进 EVEN、ALL 进两者）+ 单双周共槽对拍 + 纯 ALL 回归保护 + Delta 增量 vs 全量对拍。

### 2B 导出链（修复 R9 静默覆盖）—— commit `e52bb27`

修复 R9：单双周课程共槽时（教师周一1-2节 ODD体育 + EVEN思政），导出 Excel 与界面网格**静默丢弃**第二条课程。

**根因（后端 + 前端各一处，同形 bug）**：
- 后端 `TimetableService.createTimetableSheet`：`Collectors.toMap(..., (first,second)->first)` 同 cellKey 第二项被丢弃
- 前端 `TimetableGrid.vue`：`map[key] = item` 同 (day,period) 后者覆盖前者
- `TimetableVo` 无 weekType 字段，list API 也丢该信息

| 文件 | 改动 |
|---|---|
| `TimetableVo.java` | 加 `weekType` 字段（@Data，无全参构造器风险） |
| `WeekTypeSupport.java` | 新增 `displayLabel(String)`：ALL→无标记，ODD→`单`，EVEN→`双`，统一工具类 |
| `TimetableService.java` | `buildTimetableVo` 透传 `vo.setWeekType`；`itemMap` 从 `toMap` 改 `groupingBy` 保留多条；`buildCellText` 重载 `List<TimetableVo>` 逐条加 `[单]/[双]` 标记，多条用 `---` 分隔 |
| `frontend/.../timetable.ts` | `TimetableItem` 加 `weekType?: string`（可选，向后兼容） |
| `frontend/.../TimetableGrid.vue` | cells map 改成 `TimetableItem[]` 聚合，模板 v-for 渲染多条，courseLabel 加 `[单]/[双]`，course-block 虚线分隔 |

**显示格式（用户确认）**：拼接分隔，ALL 不加标记。ODD+EVEN 共槽单 cell = `体育[单]\n---\n思政[双]`；全周单课 = `体育`（不加 `[全]`，不污染现有显示，零回归）。

**M15 架构测试合规**：不加 Mapper（7 个不变）、保留方法名（createTimetableSheet/buildCellText/querySchedulesByTaskField）、不加 @GetMapping。

**新增测试 T6**（`TimetableExportWeekTypeTest`，4 用例）：VO 透传 weekType + 共槽不丢数据（ODD体育+EVEN思政两条都可见）+ ALL 不加标记 + teacher 视图含班级/教室。mock selectById + selectBatchIds 双路径。

### 2C V4/V5 校验链（修复 R10 误报）—— commit `9a8929f`

修复 R10：单双周课程在 V4/V5 校验链中**不误报硬冲突**（ODD+EVEN 共槽合法）。

**根因**：4 个文件全部 0 weekType 引用，接收 weekType-bearing SchedulePlanItem 数据但完全忽略。ODD+EVEN 共槽被误报为 teacher/class/room 硬冲突。

**修复范式统一**：period overlap 且 `WeekTypeSupport.overlap(a.weekType, b.weekType)` 双重满足才算真冲突。

| 文件 | 改动点 |
|---|---|
| `V5ConsistencyCheckService.java` | `checkHardConflicts` 扫描线加 weekType overlap 网关 |
| `V5RuleEvaluationService.java` | `checkHardRules` 三个 anyMatch（teacher/class/room）各加 weekType overlap 网关 |
| `V5CandidatePositionService.java` | `fastHardCheck` 四个 anyMatch + affectedItems snapshot + `collectAffectedItems` OR-predicate 全部加网关 |
| `V4ScheduleRiskService.java` | `detectSlotConflicts` group-by key 不变，组内改 peer-overlap 检查（参照 SchedulePlanService:698-706）—— 任一 pair 满足 weekType overlap 才报 |

**关键设计决策（实施中澄清并修正）**：V4 负载统计（detectTeacherOverload/detectClassDailyOverload/detectRoomUtilization）**不 β 展开**。评分链 2A 的 β 展开适用于"方差/均衡度"（归一化后纯 ALL 数值不变），但 V4 负载统计是"绝对课时数 vs 阈值"，β 展开会让纯 ALL 教师课时翻倍（非零回归，且业务语义错误——ALL 课 2 节就是 2 节）。T7 核心是修复硬冲突误报，负载统计与单双周无关。

**零回归保证**：所有现有测试 fixture item 不设 weekType → null → normalize→ALL → ALL+ALL overlap=true → 修复后行为不变。

**扩展测试 T7**（共 7 用例）：
- V5ConsistencyCheckServiceTest +2：ODD+EVEN 共槽 PASS + ALL+ODD 仍报
- V5CandidatePositionServiceTest +2：ODD 候选 vs EVEN peer 可用 + ALL 候选 vs ODD peer 不可用
- V4ScheduleRiskServiceTest +3：ODD+EVEN 共槽 0 TEACHER_CONFLICT + ALL+ODD 报冲突 + ALL+ALL 回归

## 测试结果

```
mvn test
Tests run: 320, Failures: 0, Errors: 0, Skipped: 1（V8BenchmarkComparisonTest 留阶段3）
BUILD SUCCESS
```

```
npx vue-tsc -b
（通过，无错误）
```

各阶段测试增量：2A 后 303→309，2B 后 309→313，2C 后 313→320。

## 阶段 2 整体验收（DoD 对照 V9_04:209-214）

| 项 | 状态 |
|---|---|
| 评分、导出、V4/V5 四条链路全部纳入 weekType | ✅ |
| 新增对拍与回归测试全绿（T5 6 + T6 4 + T7 7 = 17 用例） | ✅ |
| 现有测试零回归 | ✅ |
| 新增 E2E："单双周方案 rescore + 导出 + V5 一致性检查"（T11） | ⏳ 待跑（需启动后端+前端实机） |

## 阶段 2 边界声明（测试启用矩阵对照 V9_05）

| 测试 | 阶段 2 状态 |
|---|---|
| T5 评分对拍 | ✅ Service 层启用 |
| T6 导出完整性 | ✅ 启用 |
| T7 V4/V5 校验 | ✅ 启用 |
| T8 生成落库闭环 | ✅（阶段 1 已启用，本阶段不回归） |
| T11 E2E | ⏳ 阶段 2 新增 E2E 待跑 |
| T9 V8 引擎 / T10 benchmark | ❌ 仍不跑（留阶段 3） |

## 关键技术约束与坑（供阶段 3 参考）

1. **β 展开只适用于方差/均衡度，不适用于绝对计数**：V4 负载统计若误用 β 会课时翻倍。阶段 3 引擎 IncrementalPenaltyState 的 6 个 Map 要区分"方差类（β 展开）"vs"绝对计数类（不展开）"。
2. **引擎不可改签名**：ObjectiveFunction/IncrementalPenaltyState 依赖 ScoringFunctions 旧签名。阶段 3 激活引擎 β 时，引擎的 EngineTask/Assignment 需先加 weekType 字段（方案 X：TimeSlotData 加 weekType，slot 物理翻倍）。
3. **WeekTypeSupport 是单一真相源**：normalize/overlap/countableWeekTypes/displayLabel 四方法，被 DB版+V3版+引擎版(阶段3)+评分链+导出链+V4V5校验链共用。改它要同步评估六路。
4. **测试 fixture 默认不设 weekType**：这是天然零回归保护（null→ALL→overlap=true）。新测试要显式设 weekType 才能验证单双周语义。

## 下一步

- 跑阶段 2 E2E（T11 新增）—— 需启动后端 8090 + 前端 5173
- 合并 `feature/v9-stage1-data-conflict` → `feature/v9-week-type`（阶段 2 收口）
- 阶段 3：V8 引擎扩展（V9.2，激活 stub，三路冲突对拍，benchmark）
