# D3 A.3 violation_count 语义调查

日期：2026-05-26

## 结论

`schedule_score_detail.violation_count` 字段名和数据库注释写的是『违规次数』，但当前实际语义是混合量：

- 硬规则：真实违规次数，范围 `[0, N]`。
- 软规则：偏差百分比等级，范围 `[0, 100]`。

因此 A.3 判断成立：字段命名 misleading。当前不是计算 bug，属于展示/API 语义不够清晰。

## 证据

- DB 字段：
  - `backend/src/main/resources/db/migration/V1__baseline.sql`
  - `backend/src/main/resources/db/v3_score.sql`
  - 字段注释均为 `违规次数`。
- 实体：
  - `ScheduleScoreDetail.violationCount`
  - `ScheduleScoreItemVo.violationCount`
- 写入链路：
  - `ScheduleScoreService.rescore` 重算并插入 `schedule_score_detail`。
  - `buildHardMetric` 对硬规则写入 `violationCount`，含义是真实违规次数。
  - `buildSoftMetric` 对软规则写入 `level`，即 `penaltyFactor * 100` 四舍五入后的偏差百分比。
- 读取链路：
  - `ScheduleScoreController.getScoreDetails` 直接返回 `ScheduleScoreDetail`。
  - `V4ScheduleAnalysisService.getPlanScoreDetails` 转成 `ScheduleScoreItemVo`，字段仍叫 `violationCount`。
- 前端展示：
  - `SchedulePlanDetailView.vue` 显示为『违规次数』。
  - `ScheduleScoreDetail.vue` 显示为『违规/偏差值』，语义相对准确。
  - `ScheduleListCards.vue` 显示为『违规数』。

## 风险判断

不建议直接做 DB 字段重命名。原因：

- 涉及表结构、实体、VO、前端类型、历史数据、接口兼容。
- 当前字段已经被 V3/V4/V5 多处消费。
- 计算明细 `detailMessage` 已经能表达真实量纲，例如『违规 N 次』或『偏差 N%』。

## 推荐处理

优先做低风险展示收口：

1. 保留 DB 字段和 API 字段 `violation_count` / `violationCount` 不变。
2. 前端列表列名统一改为『违规/偏差值』。
3. 可选：前端按 `ruleType` 显示单位，硬规则显示『N 次』，软规则显示『N%』。
4. 后端文档补充字段真实语义：硬规则为违规次数，软规则为偏差百分比。

这样可以修掉用户可见误导，又不引入迁移风险。

## 不建议路径

- 不建议把字段直接改名为 `metric_value` 或 `penalty_level`：迁移面太大。
- 不建议把软规则写回 0：会丢失目前可用于解释的偏差等级。
- 不建议拆成两个字段后立即替换旧字段：需要兼容历史接口和前端，收益不足以覆盖风险。

