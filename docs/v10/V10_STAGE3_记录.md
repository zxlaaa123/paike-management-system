# V10 Stage 3 记录：V4/V5/V6 校验链

## 目标

让 V4 风险视图、V5 试算一致性检查、V6 回归扫描三个校验链路与阶段 2 的手动排课/方案冲突口径一致：
同资源同物理时段下，只有实际自然周集合相交才算冲突。

本阶段不改 V8 引擎、不改评分链（留给阶段 4/5）。

## 改动清单

### 1. V4ScheduleRiskService（风险视图）

`detectSlotConflicts` 的组内过滤从 `WeekTypeSupport.overlap(item.weekType, other.weekType)` 升级为
`WeekPatternSupport.overlap(item.weekType, item.startWeek, item.endWeek, other.weekType, other.startWeek, other.endWeek)`。

语义不变：组内过滤出真冲突子集——与组内其他任一项实际周集合相交的 item。ODD+EVEN 共槽、ALL 1-8 与 ALL 9-16 共槽（合法）不报。

### 2. V5ConsistencyCheckService（试算一致性检查）

`checkHardConflicts` 的 pair 判定从 `WeekTypeSupport.overlap(previous.weekType, current.weekType)` 升级为
`WeekPatternSupport.overlap(previous.weekType, previous.startWeek, previous.endWeek, current.weekType, current.startWeek, current.endWeek)`。

保留原有的 period overlap 前置条件：period overlap 且实际周集合相交才报硬冲突。

### 3. V6RegressionTestService（回归扫描）

`scanResourceConflict` 的 pair 判定从 `WeekTypeSupport.overlap(a.weekType, b.weekType)` 升级为
`WeekPatternSupport.overlap(a.weekType, a.startWeek, a.endWeek, b.weekType, b.startWeek, b.endWeek)`。

冲突提示信息从 `WeekTypeSupport.normalize(weekType)` 改为 `WeekPatternSupport.displayLabel(weekType, startWeek, endWeek)`，现在会显示完整周段标签（如 `1-8周`、`5-12周/单`），便于排查。

### 4. V6ConsistencyCheckService

无需改动。该服务只是一致性检查记录的查询服务，不涉及冲突判定逻辑。

## 测试补充

### V4ScheduleRiskServiceTest（+2 用例）

| 用例 | 场景 | 预期 |
|---|---|---|
| `getPlanRisks_disjointWeekRangeNotReportedAsConflict` | ALL 1-8 与 ALL 9-16 同教师同槽 | 0 TEACHER_CONFLICT |
| `getPlanRisks_overlappingWeekRangeReportedAsConflict` | ALL 1-8 与 ODD 5-12 同教师同槽 | 1 TEACHER_CONFLICT |

### V5ConsistencyCheckServiceTest（+2 用例）

| 用例 | 场景 | 预期 |
|---|---|---|
| `check_disjointWeekRangeNotReportedAsConflict` | ALL 1-8 与 ALL 9-16 同资源同时段重叠 | PASS，0 blocking |
| `check_overlappingWeekRangeReportedAsConflict` | ALL 1-8 与 ODD 5-12 同资源同时段重叠 | FAIL，3 硬冲突 |

新增 `item(...)` 重载支持 startWeek/endWeek 参数，原 9 参数签名保留向后兼容。

### V6RegressionTestServiceRunTest（+2 用例）

| 用例 | 场景 | 预期 |
|---|---|---|
| `run_passesWhenDisjointWeekRangeShareSlot` | ALL 1-8 与 ALL 9-16 同时段同资源 | 全 PASS（4/4） |
| `run_detectsOverlappingWeekRangeTeacherConflict` | ALL 1-8 与 ODD 5-12 同时段同资源 | 教师冲突自检 FAIL |

新增 `schedule(...)` 重载支持 startWeek/endWeek 参数，原 4 参数签名保留向后兼容。

## 验证

### 后端

命令：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='123456'
$env:JWT_SECRET='dev_local_secret_please_change_32_chars_minimum'
cd D:\paike\backend
mvn -q "-Dtest=V4ScheduleRiskServiceTest,V5ConsistencyCheckServiceTest,V6RegressionTestServiceRunTest,WeekPatternSupportTest,WeekTypeConflictMatrixTest,ScheduleConflictServiceTest,SchedulePlanServiceTest,ScheduleConflictReportServiceTest,V9WeekTypeBenchmarkTest" test
```

结果：全部通过（BUILD SUCCESS，无 FAILED）。

- V10 周段红线用例（6 条新增）全过
- V9 单双周回归（`V9WeekTypeBenchmarkTest`、原 ODD+EVEN 共槽用例、ALL+ODD 冲突用例）无回退
- 阶段 2 冲突检测测试无回退

## 边界

本阶段未做（留给后续阶段）：

- V8 引擎 `InMemoryConflictDetector` 接入 `WeekPatternSupport.overlap`（阶段 4）
- `AutoScheduleService.applyWeekTypeOverlapFilter` V9 三值逻辑升级（阶段 4）
- 评分链 daily limit 按实际自然周展开计数（阶段 5）
- `ScheduleMapper.selectDailyConflictCounts` SQL 周段精确过滤（阶段 5）

## 完成定义核对

| 完成定义 | 状态 |
|---|---|
| V4/V5/V6 对同一组数据给出同样冲突结论 | ✅ 三条链路统一用 `WeekPatternSupport.overlap`，与阶段 2 的手动排课/方案冲突/冲突报告同源 |
| V9 `ODD + EVEN` 共槽测试继续通过 | ✅ 原 V9 用例 + `V9WeekTypeBenchmarkTest` 全过 |

下一阶段应进入 `V10_02_开发阶段计划.md` 的阶段 4：V8 引擎与自动生成。
