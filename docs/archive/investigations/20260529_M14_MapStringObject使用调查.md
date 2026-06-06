# M-14 Map<String, Object> 使用调查

## 结论

部分成立，暂不修复生产代码。

原问题成立：当前后端主代码确实检出 16 个 Java 文件、66 处 `Map<String, Object>`。

本轮分类结果：

- Controller 层：21 处。
- Service 层：45 处。
- 公开接口或服务契约：30 处。
- 内部临时组装：15 处。
- 内部集合/循环处理：6 处。
- 局部变量或其他辅助处理：15 处。

使用最集中的文件：

- `ScheduleStatisticsService.java`：23 处。
- `SchedulePlanService.java`：10 处。
- `ScheduleCompareService.java`：7 处。
- `ScheduleStatisticsController.java`：5 处。
- `SchedulePlanController.java`：4 处。

## 判断

这属于类型约束和接口可维护性问题，不是单点运行 bug。

本轮不做机械 DTO 化，原因：

- 多数 `Map<String, Object>` 已经是 Controller 返回值或 Service 公共方法返回值，替换会改变接口契约和前端字段依赖。
- 统计接口天然存在动态聚合字段，例如教师工作量、教室利用率、班级均衡、仪表盘概览。
- `SchedulePlanService`、`ScheduleCompareService` 的返回结构被多个 Controller / V5 流程复用，直接替换需要先定义稳定 VO。

## 后续建议

如果要继续治理，建议按模块拆分，不要全局批量替换：

1. 优先为 `ScheduleStatisticsService` 定义统计 VO，因为它占比最高且返回结构相对稳定。
2. 再处理 `SchedulePlanService` 的 apply/rollback/adjust 返回结构。
3. 最后处理少量健康检查、登出等简单响应。

## 本轮验证

新增 `M14MapStringObjectUsageInvestigationTest` 锁定当前事实：

- 后端主代码仍有 66 处 `Map<String, Object>`。
- 分布在 16 个 Java 文件。
- Controller / Service 分布分别为 21 / 45。
- `ScheduleStatisticsService`、`SchedulePlanService`、`ScheduleCompareService` 是主要集中点。

