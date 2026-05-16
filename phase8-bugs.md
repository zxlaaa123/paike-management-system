# 阶段8 Bug 分析与修复建议

## ScheduleStatisticsService.java

### 1. `getPeriodCount` 对 `Schedule` 硬编码返回 2
- **位置**: 第387行
- **问题**: 正式课表每条记录的课时数可能不同，硬编码返回 2 会导致统计不准确。
- **建议**: 给 `Schedule` 实体添加 `periodCount` 字段，或通过 `timeSlotId` 关联 `TimeSlot` 获取实际课时数。

### 2. `TOTAL_AVAILABLE_PERIODS` 硬编码为 20
- **位置**: 第113行
- **问题**: 实际课表可能不是 5天×4大节 的安排。
- **建议**: 从 `TimeSlot` 表查询实际时间段数量，或作为参数传入。

### 3. `calculateBalanceScore` 除以 5 硬编码
- **位置**: 第411行
- **问题**: 如果一周上课天数不是 5 天，计算会不准确。
- **建议**: 从 `TimeSlot` 表查询实际上课天数（`SELECT COUNT(DISTINCT dayOfWeek) FROM time_slot`）。

### 4. 辅助方法返回 `null` 无空值检查
- **位置**: 第343-365行
- **问题**: `getTeacherId`、`getCourseId`、`getClassId`、`getRoomId` 返回 `null`，但调用方没有空值检查，可能导致 `NullPointerException`。
- **建议**: 在调用处加空值判断，跳过 `teacherId`/`courseId`/`classId`/`roomId` 为 `null` 的记录，或抛出明确的业务异常。

### 5. `getWeekday` 返回 0 表示未知
- **位置**: 第374行
- **问题**: 0 不是有效的星期几（应为 1-7），可能导致后续计算错误。
- **建议**: 返回 -1 或抛异常，并在调用处过滤掉 weekday <= 0 的记录。

## ScheduleStatisticsController.java

### 6. `resolveSemesterId` 中 `getCurrentSemester()` 可能返回 `null`
- **位置**: 第91行
- **问题**: 如果当前未设置学期，调用 `.getId()` 会抛出 `NullPointerException`。
- **建议**: 先判空，返回明确错误信息：
```java
Semester semester = semesterService.getCurrentSemester();
if (semester == null) throw new BusinessException("当前未设置学期");
return semester.getId();
```

## 前端代码

### 7. `:value="undefined"` 可能不生效
- **位置**: ScheduleStatisticsView.vue 第182行
- **问题**: Element Plus 的 `el-option` 中 `:value="undefined"` 可能不会按预期工作。
- **建议**: 改为 `:value="null"` 或 `value=""`，并在 `onPlanChange` 中判断空值时视为"正式课表"。

### 8. `getCurrentSemester().catch(() => null)` 静默吞掉所有错误
- **位置**: ScheduleStatisticsView.vue 第36行
- **问题**: 网络错误等异常会被静默吞掉，用户无法感知。
- **建议**: 区分处理，网络错误应提示用户，只有 404/无数据时才静默返回 `null`。
