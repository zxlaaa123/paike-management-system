# V10 阶段 6 记录：导出、网格、前端最小闭环

## 目标
让用户能创建周段任务，并在课表和 Excel 中看懂结果。

## 改动清单

### 后端
1. `TimetableService.buildTimetableVo`：透传 `schedule.getStartWeek()`/`getEndWeek()` 到 `TimetableVo`
2. `TimetableService.buildCellText`：用 `WeekPatternSupport.displayLabel(weekType, startWeek, endWeek)` 替代 `WeekTypeSupport.displayLabel(weekType)` 生成周段标签

### 前端
1. `frontend/src/api/timetable.ts`：`TimetableItem` 接口加 `startWeek`/`endWeek` 可选字段
2. `frontend/src/components/TimetableGrid.vue`：`weekLabel` 升级为 `weekRangeLabel`，与后端 `WeekPatternSupport.displayLabel` 同语义

## 展示规则
| 周段 | weekType | 标签 | 示例 |
|---|---|---|---|
| 1-20（默认） | ALL | 无 | `数学` |
| 1-20（默认） | ODD | `单` | `体育[单]` |
| 1-20（默认） | EVEN | `双` | `思政[双]` |
| 1-8 | ALL | `1-8周` | `数学[1-8周]` |
| 5-12 | ODD | `5-12周/单` | `体育[5-12周/单]` |
| 9-16 | ALL | `9-16周` | `思政[9-16周]` |

## 验证结果

### 红线测试
- `TimetableExportWeekRangeTest`：5/5 通过
  - VO 透传 startWeek/endWeek
  - 默认周段无标签（零回归）
  - 非默认周段显示 [1-8周]
  - 周段+单双周显示 [5-12周/单]
  - 同 cell 多周段不覆盖（ALL 1-8 + ALL 9-16 共槽）

### V9 回归
- `TimetableExportWeekTypeTest`：7/7 通过（纯 ALL + ODD/EVEN 单双周导出零回归）

### 前端
- `vue-tsc --noEmit` 通过

### 全量后端
- 384 测试全过，0 失败，2 skip

## 完成定义核对
- ✅ Excel 可读出所有同槽课程（buildCellText 多条拼接）
- ✅ 周段标签准确（WeekPatternSupport.displayLabel）
- ✅ 前端类型检查通过
