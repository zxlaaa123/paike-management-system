# 排课系统数据库初始化说明

本目录是排课后端的 schema 仓库。本项目**有意不上 Flyway/Liquibase**，初始化由三套入口组合完成。本文档定义三套入口的**执行顺序**与**职责边界**，对应修复计划 P2-7 验收第二条："若继续保留多个初始化入口，必须明确执行顺序和职责边界"。

> 历史决策：自用项目，DDL 已稳定持久化，多入口模式不再大改。详见 `修复计划2026522.md` line 173。

---

## 1. 执行顺序

Spring Boot 启动时依次执行：

| 阶段 | 入口 | 触发点 | 备注 |
|---|---|---|---|
| ① | `db/schema.sql` | `spring.sql.init` | 全新库建表基线 |
| ② | `db/v2_*.sql` → `v12_*.sql`（按 `application.yml` 列出的顺序） | `spring.sql.init` | 增量演进 ALTER/CREATE，全部幂等 |
| ③ | `SemesterSchemaInitializer.run()` | `CommandLineRunner`，在 sql.init 之后 | 运行时兜底（含 Java 代码内嵌的 CREATE TABLE / ALTER） |
| ④ | `AdminUserInitializer.run()` | `CommandLineRunner` | 创建默认 admin 账号 |

`spring.sql.init.continue-on-error: true`：单个 SQL 失败不中断启动，由后续阶段或 ③ 的兜底补齐。**这是有意设计**，因为 v2/v6 早期文件用 `DROP PROCEDURE` 包 DDL，对老库重跑可能局部报错，但语义已经满足，不应阻塞启动。

---

## 2. 各 SQL 文件职责

### schema.sql — 全新库基线

`CREATE TABLE IF NOT EXISTS` 风格，建出最初版的 11 张核心表（`sys_user / teacher / class_info / classroom / course / time_slot / teaching_task / schedule / login_log` 等）。**只建表、不演进**。

### v2_*.sql — 排课规则与冲突报告

| 文件 | 内容 |
|---|---|
| `v2_schema.sql` | 新增 7 张表：`teacher_unavailable_time` / `schedule_rule_config` / `auto_schedule_batch` / `unscheduled_task` / `schedule_conflict_report` / `schedule_score_report` + 默认规则初始化 |
| `v2_alter_schedule.sql` | 给 `schedule` 表加 `source_type` / `batch_id`（DROP PROCEDURE 幂等模式）|
| `v2_alter_score_report.sql` | 给 `schedule_score_report` 加 `grade_name` |

### v3_*.sql — 学期管理与排课方案

| 文件 | 内容 |
|---|---|
| `v3_semester.sql` | 建 `semester` 表 + 插默认学期（如果库里没有任何学期）|
| `v3_semester_data_bind.sql` | 给 `teaching_task` / `schedule` 加 `semester_id` / `plan_id`，迁移旧数据 |
| `v3_schedule_plan.sql` | 建 `schedule_plan` / `schedule_plan_item` 两张方案核心表 |
| `v3_score.sql` | 建 `schedule_rule_weight` / `schedule_score_detail` + 插四种策略的默认权重 |

### v5_*.sql — V5 试算 / 修复任务

| 文件 | 内容 |
|---|---|
| `v5_stage1.sql` | `schedule_plan.plan_mode` 标记试算 + 建 `schedule_repair_task` / `schedule_repair_suggestion` / `schedule_candidate_position` / `schedule_optimization_compare` / `schedule_consistency_check` / `schedule_regression_test` 6 张表 |
| `v5_stage3.sql` | `schedule_repair_task.title` 字段 |
| `v5_stage6.sql` | `schedule_plan.source_schedule_id` 字段 |

### v6_*.sql — bugfix 与并发约束

| 文件 | 内容 | 备注 |
|---|---|---|
| `v6_bugfix_constraints.sql` | `teacher_unavailable_time.active_key` 生成列 + `uk_teacher_timeslot` 唯一索引；`schedule_plan_item.uk_plan_task_slot`；`schedule_locked_item.active_key` + `uk_locked_plan_item` / `uk_locked_schedule` 唯一索引 | **用了 `DELIMITER + CREATE PROCEDURE`，Spring `ScriptUtils` 不正式支持**，但本机已确认生效。**不改写为 v7 风格**（DDL 已持久化）|
| `v6_schedule_index.sql` | `schedule` 表的业务索引和唯一约束（TOCTOU 防护）| PROCEDURE 风格 |
| `v6_semester_current_unique.sql` | `semester.is_current=1` 至多一行的并发兜底（虚拟生成列 + UNIQUE）| PROCEDURE 风格 |

### v7_*.sql — 软删除收尾

| 文件 | 内容 |
|---|---|
| `v7_soft_delete_plan_semester.sql` | `schedule_plan` / `schedule_plan_item` / `semester` 加 `deleted` 列。**使用 `SET @ddl + PREPARE/EXECUTE` 模式**，是 Spring ScriptUtils 正式支持的幂等写法，新 SQL 文件应参考此风格 |

### v8_*.sql - v12_*.sql — 后续补丁与 V6 审计基础

| 文件 | 内容 |
|---|---|
| `v8_unscheduled_task_semester.sql` | 未排任务学期字段补齐 |
| `v9_auto_schedule_batch_update_time.sql` | 自动排课批次更新时间字段补齐 |
| `v10_teaching_task_index.sql` | 教学任务索引补齐 |
| `v11_soft_delete_three_tables.sql` | 锁定、调整日志、未排任务软删除字段补齐 |
| `v12_system_audit_log.sql` | V6 第一阶段：新增 `system_audit_log` 审计日志表 |
| `v14_missing_v4_v5_tables_and_schedule_keys.sql` | C-21/C-22：固化 5 张 V4/V5 表 DDL，修正 `schedule` 软删除安全唯一键 |
| `v15_sys_user_role.sql` | C-17：给 `sys_user` 增加 `role`，内置 `admin` 标记为 `ADMIN` |

---

## 3. Java 兜底：`SemesterSchemaInitializer`

位置：`com.paike.scheduler.config.SemesterSchemaInitializer`。`CommandLineRunner`，在 sql.init 之后跑。**职责是兼容旧库**（早于某些 v\*.sql 文件就跑过、缺列缺索引的库），不做新数据库初始化。

每个 `ensure*` 方法的职责定位：

| 方法 | 与哪个 SQL 文件重叠 | 兜底逻辑 |
|---|---|---|
| `ensureSemesterIndexes` | 无（早期 `v3_semester.sql` 没建索引）| 唯一职责：补 `idx_semester_current` / `idx_semester_school_year` |
| `ensureTeachingTaskSemesterColumn` | 与 `v3_semester_data_bind.sql` 第 1 项重叠 | 兜底（极旧库可能没跑过 v3） |
| `ensureScheduleSemesterColumns` | 与 `v3_semester_data_bind.sql` 第 2 项重叠 | 兜底 |
| `ensureScheduleScoreDetailColumns` | 无（`rule_type` 列只在 Java 里加过）| 唯一职责 |
| `ensureScheduleRuleWeightUniqueIndex` | 无（v3_score.sql 当初没建唯一索引）| 唯一职责，含清理重复数据 |
| `ensureStage7Tables`（4 张表：`schedule_generate_log` / `schedule_unassigned_task` / `schedule_adjust_log` / `schedule_locked_item`）| **`schedule_locked_item` 与 `v6_bugfix_constraints.sql` 部分重叠**（active_key、唯一索引）| 这 4 张表**没有对应的 v\*.sql 文件**，CREATE TABLE 完全在 Java 里。其他三张表唯一职责 |
| `ensureStage9Tables`（`schedule_report`）| 无 | 唯一职责，没有对应 SQL 文件 |

**冗余说明**：`schedule_locked_item.active_key` 在三处定义（Initializer 的 CREATE TABLE 内嵌 / Initializer 的独立 ALTER / `v6_bugfix_constraints.sql`），全部幂等，结果一致。本项目接受这个冗余，不做收敛——理由：删任何一处都需要确认 100% 的部署环境已经过了对应版本，对自用项目不值。

---

## 4. 新增 schema 改动时的约定

1. **新文件命名**：使用下一个可用序号；当前最后一项为 `v12_system_audit_log.sql`。
2. **幂等写法**：优先 `SET @ddl + PREPARE/EXECUTE`（参见 `v7_soft_delete_plan_semester.sql`），避开 `DELIMITER + CREATE PROCEDURE`（Spring ScriptUtils 不正式支持）。
3. **注册到 `application.yml`**：把新文件加到 `spring.sql.init.schema-locations` 末尾。
4. **不要扩 `SemesterSchemaInitializer`**：能写在 SQL 文件的就不写 Java。Initializer 只为兜底极老库。
5. **新表慎在 Initializer 里建**：`ensureStage7/9Tables` 是历史遗留，新表应该写在 v\*.sql 文件里。

---

## 5. 部署到新环境的人工 checklist

`continue-on-error: true` 会吞掉真实失败，部署到新机器后请人工核查关键约束已生效：

```sql
-- 1) schedule_locked_item 的唯一约束（避免重复锁）
SHOW INDEX FROM schedule_locked_item WHERE Key_name IN ('uk_locked_plan_item','uk_locked_schedule');

-- 2) teacher_unavailable_time 的唯一约束（避免重复禁排）
SHOW INDEX FROM teacher_unavailable_time WHERE Key_name = 'uk_teacher_timeslot';

-- 3) schedule_plan_item 的唯一约束（避免方案明细重排同一节）
SHOW INDEX FROM schedule_plan_item WHERE Key_name = 'uk_plan_task_slot';

-- 4) schedule 的软删除安全唯一约束（避免正式课表并发冲突）
SHOW COLUMNS FROM schedule LIKE 'active_key';
SHOW INDEX FROM schedule WHERE Key_name IN ('uk_schedule_teacher_slot','uk_schedule_class_slot','uk_schedule_classroom_slot');

-- 5) semester 至多一个 current 学期的唯一约束
SHOW INDEX FROM semester WHERE Column_name = 'current_marker';

-- 6) 软删除列
SHOW COLUMNS FROM schedule_plan      LIKE 'deleted';
SHOW COLUMNS FROM schedule_plan_item LIKE 'deleted';
SHOW COLUMNS FROM semester           LIKE 'deleted';
```

任一项缺失，对应的 v\*.sql 没成功跑——查启动日志的 `Failed to execute SQL script` 段落。
