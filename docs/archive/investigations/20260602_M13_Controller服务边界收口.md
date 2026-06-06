# 20260602 M13 Controller 服务边界收口

## 结论

`M-13` 已修复。当前 `backend/src/main/java/com/paike/scheduler/controller` 下不再检出 `private final *Mapper` 字段。

## 调查

- M-15 收口后，剩余直接注入 Mapper 的 Controller 为 `ScheduleController`、`TeachingTaskController`、`TimeSlotController`。
- 问题性质为结构性可维护性问题，不是已复现功能故障。

## 修改

- 新增 `ScheduleService`，承接排课列表、详情、创建、删除、按班级/教师/教室查询、冲突检测和关联字段填充。
- 新增 `TeachingTaskService`，承接教学任务列表、详情、创建、更新、删除、全部启用任务和关联字段填充。
- 新增 `TimeSlotService`，承接时间段列表、详情、按星期查询。
- `ScheduleController`、`TeachingTaskController`、`TimeSlotController` 收敛为参数接收和 `Result` 包装。
- 更新 `M13ControllerMapperInjectionInvestigationTest`，动态扫描所有 Controller 并断言 Mapper 字段为 0。
- 同步更新受构造器变化影响的 `ControllerNotFoundStatusTest` 与 `ScheduleControllerTest`。

## 验证

- `cd D:\paike\backend; mvn -Dtest=M13ControllerMapperInjectionInvestigationTest test`
  - 1 test，0 failures，0 errors。
- `cd D:\paike\backend; mvn -Dtest=!SchedulerBackendApplicationTests test`
  - 151 tests，0 failures，0 errors。
