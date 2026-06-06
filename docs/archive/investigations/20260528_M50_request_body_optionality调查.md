# 20260528 M-50 request body optionality 调查

## 结论

`M-50` 不作为缺陷修复项处理，当前更像接口契约设计。

原因：

- 6 个候选端点里，绝大多数都明确把 `request == null` 当成默认值处理。
- `ScheduleReportController.generatePlanReport()`、`ScheduleReplanController.createLocalReplanPlan()`、`ScheduleAiAnalysisController.generatePlanAiAnalysis()` 都允许空 body，然后由 service 端决定默认行为。
- `V5SimulationController.localReplan()` 虽然在 controller 上写了 `@RequestBody(required = false)`，但 service 入口已经通过事务模板和默认请求处理接住。
- 真正带“空 body 也能跑”的业务点是 `V5RepairTaskController.cancel()`，它把空请求转换成 `cancelReason == null`，再由 service 端按默认 reason 处理。

## 证据

明显是默认契约而非隐藏 bug 的端点：

- `ScheduleAiAnalysisController.generatePlanAiAnalysis()`
- `ScheduleReplanController.createLocalReplanPlan()`
- `ScheduleReportController.generatePlanReport()`
- `V5SimulationController.localReplan()`

设计上显式接受空 body 的端点：

- `V5RepairTaskController.cancel()`

## 已执行验证

新增测试：

- `backend/src/test/java/com/paike/scheduler/service/M50RequestBodyOptionalityInvestigationTest.java`

验证内容：

- 空 body 在这些端点中被当作默认值，而不是空指针缺陷。
- `cancelTask` 接受 `null` reason，并以 `trimToNull` 收口。

验证命令：

- `cd D:\paike\backend; mvn -Dtest=M50RequestBodyOptionalityInvestigationTest test`

## 当前判断

`M-50` 属于接口设计/默认值契约，不建议当前改代码。

如果后续要继续推进，只需要做一件事：

1. 给这些端点补清晰的契约说明或 controller 测试，避免别人误以为 `required=false` 是遗漏。
