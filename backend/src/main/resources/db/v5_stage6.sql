-- =============================================
-- V5 Stage 6: 试算方案生成
-- =============================================

ALTER TABLE schedule_plan
    ADD COLUMN source_plan_id BIGINT NULL COMMENT '试算来源方案ID' AFTER id;

ALTER TABLE schedule_plan
    ADD COLUMN source_schedule_id BIGINT NULL COMMENT '试算来源正式课表ID' AFTER source_plan_id;

ALTER TABLE schedule_plan
    ADD COLUMN repair_task_id BIGINT NULL COMMENT '绑定修复任务ID' AFTER source_schedule_id;

ALTER TABLE schedule_plan
    ADD COLUMN plan_mode VARCHAR(30) NULL COMMENT '方案模式：NORMAL、SIMULATION' AFTER strategy_type;

CREATE INDEX idx_plan_source_plan ON schedule_plan(source_plan_id);
CREATE INDEX idx_plan_source_schedule ON schedule_plan(source_schedule_id);
CREATE INDEX idx_plan_repair_task ON schedule_plan(repair_task_id);

