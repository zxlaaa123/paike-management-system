# M-16 第7批收口报告 —— SchedulePlan

日期：2026-06-03　分支：`refactor/m16-batch7-schedule-plan-vo`

---

## 1. 特殊性

`SchedulePlan` 的 2 个 view 字段 `semesterName`/`strategyName` **全库无 setter**（`grep` 验证），恒 null。属漏填 bug（类似 `requiredSlots`），但本批仅做治理（删字段改 VO），不修 bug。

本批另一特殊性：`getById` 被 `ScheduleScoreController.rescore` 内部调用（`ScheduleScoreService.rescore → buildScoreContext` 深链用 Entity），故**仅在 Controller 层转 VO**，Service 保持 Entity。

## 2. 改面

| 层 | 文件 | 改动 |
|---|---|---|
| ① VO | `service/vo/SchedulePlanVo.java`（新建） | 22 字段（20 持久化 + 2 view）+ `fromEntity` |
| ② Entity | `entity/SchedulePlan.java` | 删 2 view 字段（`@TableField` import 保留） |
| ④ Controller | `SchedulePlanController.java` | `getById`→`Result<SchedulePlanVo>`，`fromEntity` 转换 |

## 3. 收口

测试：14 tests, 0 failures（序列化 2 + 投资 2 + PlanService 4 + ScoreService 5 + Pagination 1）。

view 字段命中 **20→18**，承载 Entity **3→2**，M-16 进度 **6/9→7/9**。
