# V10 Stage 2 记录：手动排课与方案冲突链

## 目标

让 DB 版手动排课、方案内冲突刷新、冲突报告三方口径统一到 V10 周段语义：
同资源同物理时段下，只有实际自然周集合相交才算冲突。

本阶段不改 V8 引擎、不改评分链、不改 daily limit 精确计数（留给阶段 4/5）。

## 改动清单

### 1. ScheduleConflictService（手动排课冲突检测）

`checkConflict` 三处资源冲突判定从 `WeekTypeSupport.overlap(weekTypeA, weekTypeB)` 升级为
`WeekPatternSupport.overlap(weekTypeA, startWeekA, endWeekA, weekTypeB, startWeekB, endWeekB)`：

- 第 7 条 教师冲突：existingTask 取 `weekType/startWeek/endWeek`
- 第 8 条 班级冲突：existingTask 取 `weekType/startWeek/endWeek`
- 第 9 条 教室冲突：schedule 行取 `weekType/startWeek/endWeek`

当前任务的周段从 `TeachingTaskVo.startWeek/endWeek` 取（阶段 1 已透传，`selectConflictCheckById` 的 `tt.*` 自动映射）。

### 2. SchedulePlanService（方案内冲突刷新）

`addGroupedConflictReasons` 的 pair 判定从 `WeekTypeSupport.overlap(item, other)` 升级为
`WeekPatternSupport.overlap(item.weekType, item.startWeek, item.endWeek, other.weekType, other.startWeek, other.endWeek)`。

语义不变：每条 item 只有在组内存在另一条实际周集合相交的 item 时才标记冲突。

### 3. ScheduleConflictReportService（冲突报告）

三个资源冲突 detect 方法（`detectTeacherConflicts` / `detectClassConflicts` / `detectClassroomConflicts`）
原来只看 `group.size() > 1`，会误报 ODD+EVEN 共槽、ALL 1-8 与 ALL 9-16 共槽等合法场景。

新增辅助方法 `filterWeekOverlapSchedules(List<Schedule> group)`：
- 遍历组内每条 schedule，判断是否存在另一条 `WeekPatternSupport.overlap=true` 的 pair
- 只返回参与重叠的 schedule 列表
- 组内无任何重叠 pair 时返回空列表，不报冲突

三个 detect 方法改用 `filterWeekOverlapSchedules` 的结果替代原 `group`，报告中的 size/courseSummary/relatedIds 都基于真正冲突的子集。

`detectTeacherDailyOverload` / `detectClassDailyOverload` 暂保留 `group.size()` 计数，加注释标注 V10 边界：
V9 数据（startWeek/endWeek 均默认 1-20）下行为等价；周段数据的精确日上限计数留给阶段 5 评分链统一处理。

### 4. ScheduleMapper.selectDailyConflictCounts

保留 V9 的 SQL `weekType` 分支不变。理由：
- 只有 `ScheduleConflictService` 一处调用
- V9 数据下行为等价（`overlap(ALL,1,20, ODD,1,20)` = true，与 V9 `overlap(ALL,ODD)` 一致）
- 周段数据的 daily limit 精确计数涉及评分口径，阶段 5 统一改造

## 测试补充

### ScheduleConflictServiceTest（+4 用例）

| 用例 | A | B | 预期 |
|---|---|---|---|
| `checkConflict_allWeekRange_1_8_vs_9_16_noConflict` | ALL 1-8 | ALL 9-16 | 不冲突（教师） |
| `checkConflict_all_1_8_vs_odd_5_12_conflict` | ALL 1-8 | ODD 5-12 | 冲突（教师） |
| `checkConflict_odd_1_8_vs_even_8_12_noConflict` | ODD 1-8 | EVEN 8-12 | 不冲突（教室） |
| `checkConflict_odd_1_9_vs_odd_8_12_conflict` | ODD 1-9 | ODD 8-12 | 冲突（班级） |

### SchedulePlanServiceTest（+2 用例）

| 用例 | 场景 | 预期 |
|---|---|---|
| `refreshPlanConflictState_disjointWeekRangeNoConflict` | ALL 1-8 与 ALL 9-16 同资源同时段 | 0 冲突 |
| `refreshPlanConflictState_overlappingWeekRangeConflicts` | ALL 1-8 与 ODD 5-12 同资源同时段 | 2 冲突 |

### ScheduleConflictReportServiceTest（+2 用例）

| 用例 | 场景 | 预期 |
|---|---|---|
| `generate_disjointWeekRangeNoConflictReport` | ALL 1-8 与 ALL 9-16 同教师同时段 | 不插入任何报告 |
| `generate_overlappingWeekRangeReportsConflict` | ALL 1-8 与 ODD 5-12 同教师同时段 | 插入 TEACHER_CONFLICT |

## 验证

### 后端

命令：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='123456'
$env:JWT_SECRET='dev_local_secret_please_change_32_chars_minimum'
cd D:\paike\backend
mvn -q "-Dtest=ScheduleConflictServiceTest,SchedulePlanServiceTest,ScheduleConflictReportServiceTest,WeekPatternSupportTest,WeekTypeConflictMatrixTest,V9WeekTypeBenchmarkTest,ScoringWeekTypeConsistencyTest,M16TeachingTaskVoSerializationTest,M16ScheduleVoSerializationTest,M16PlanItemVoSerializationTest" test
```

结果：全部通过（BUILD SUCCESS，无 FAILED）。

- V10 周段红线用例（8 条新增）全过
- V9 单双周回归（`V9WeekTypeBenchmarkTest` / `ScoringWeekTypeConsistencyTest` / 原 ODD+EVEN 共槽用例）无回退
- 序列化守卫无回退

## 边界

本阶段未做（留给后续阶段）：

- `ScheduleMapper.selectDailyConflictCounts` SQL 周段精确过滤（阶段 5）
- `detectTeacherDailyOverload` / `detectClassDailyOverload` 按实际自然周展开计数（阶段 5）
- V8 引擎 `InMemoryConflictDetector` 接入 `WeekPatternSupport.overlap`（阶段 4）
- V4/V5/V6 校验链接入（阶段 3）
- `AutoScheduleService.applyWeekTypeOverlapFilter` V9 三值逻辑升级（阶段 4，与引擎一起改）

## 完成定义核对

| 完成定义 | 状态 |
|---|---|
| 同资源同槽不同实际周集合不报冲突 | ✅ 三条链路统一用 `WeekPatternSupport.overlap` |
| 同资源同槽实际周集合相交必报冲突 | ✅ 三条链路 + 红线测试覆盖 |
| 方案应用前冲突刷新和手动排课结论一致 | ✅ 同源 `WeekPatternSupport.overlap` |
| V9 单双周回归无回退 | ✅ `V9WeekTypeBenchmarkTest` 等通过 |

下一阶段应进入 `V10_02_开发阶段计划.md` 的阶段 3：V4/V5/V6 校验链。
