# 20260528 C-13 baseline unique key mismatch 调查

## 结论

`C-13` 部分成立，但不建议当前直接改生产 SQL。

原因：

- `V1__baseline.sql` 中确实存在与后续脚本不同的 `CASE WHEN deleted = 0 THEN 0 ELSE NULL END` 写法。
- `v2_schema.sql` 和 `v6_bugfix_constraints.sql` 统一使用了 `active_key` 方案，语义是“只对未删除记录参与唯一约束”。
- 这说明历史基线和后续增量迁移的表达方式不一致，但当前增量脚本已经把运行时约束收口到 `active_key`。
- 没有直接复现证据证明 `V1` 当前会导致真实唯一约束破坏；更像是历史脚本写法差异，需要文档化而不是立刻重构。

## 对比结果

### `V1__baseline.sql`

- `teacher_unavailable_time` / 其他软删除相关表使用旧写法：
  `CASE WHEN deleted = 0 THEN 0 ELSE NULL END`

### `v2_schema.sql`

- `teacher_unavailable_time` 改为 `active_key`：
  `CASE WHEN deleted = 0 THEN 0 ELSE NULL END` 仍存在，但配套唯一键已按 `active_key` 生效。

### `v6_bugfix_constraints.sql`

- 进一步统一到：
  - `active_key`
  - `uk_teacher_timeslot (teacher_id, time_slot_id, active_key)`
  - `uk_plan_task_slot (plan_id, teaching_task_id, weekday, start_period, end_period)`
  - `uk_locked_plan_item`
  - `uk_locked_schedule`

## 已执行验证

现有 `DatabaseSchemaScriptTest` 已覆盖：

- `v14_missing_v4_v5_tables_and_schedule_keys.sql`
- `v16_schedule_search_order_index.sql`
- `v17_report_batch_semester.sql`
- `v18_schedule_report_semester_deleted.sql`
- `v19_score_detail_deleted.sql`

这些测试已经证明后续迁移脚本是按 `active_key` / 幂等补列方式在收口。

## 当前判断

`C-13` 不是当前优先修复项。

更合理的处理方式是：

1. 在调查报告里把 `V1` 的历史差异单独标记出来。
2. 保持后续迁移和测试基线不变。
3. 如果后面要统一脚本风格，再开专门的 schema cleanup ticket。

## 下一步

如果继续往下，建议先做 `M-13` / `M-15` 这类结构性可维护性项，或者回到 `C-24/C-25` 的前端静态一致性问题。
