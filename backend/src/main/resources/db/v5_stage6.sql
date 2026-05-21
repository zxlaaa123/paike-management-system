-- =============================================
-- V5 Stage 6: 试算方案生成
-- =============================================

ALTER TABLE schedule_plan
    ADD COLUMN source_schedule_id BIGINT NULL COMMENT '试算来源正式课表ID' AFTER source_plan_id;

ALTER TABLE schedule_plan
    ADD COLUMN repair_task_id BIGINT NULL COMMENT '绑定修复任务ID' AFTER source_schedule_id;

CREATE INDEX idx_plan_source_schedule ON schedule_plan(source_schedule_id);
CREATE INDEX idx_plan_repair_task ON schedule_plan(repair_task_id);
