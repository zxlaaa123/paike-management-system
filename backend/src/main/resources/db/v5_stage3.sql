-- =============================================
-- V5 Stage 3: 修复任务管理扩展
-- =============================================

ALTER TABLE schedule_repair_task
    ADD COLUMN title VARCHAR(200) NULL COMMENT '修复任务标题' AFTER task_code;

ALTER TABLE schedule_repair_task
    ADD COLUMN risk_item_ids TEXT NULL COMMENT '关联风险项ID列表(JSON)' AFTER risk_types;

ALTER TABLE schedule_repair_task
    ADD COLUMN scope_plan_item_ids TEXT NULL COMMENT '修复范围方案明细ID列表(JSON)' AFTER risk_item_ids;

ALTER TABLE schedule_repair_task
    ADD COLUMN cancel_reason VARCHAR(500) NULL COMMENT '取消原因' AFTER error_message;

CREATE INDEX idx_repair_task_result_plan ON schedule_repair_task(result_plan_id);

