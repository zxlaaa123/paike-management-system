-- =============================================
-- V5 Stage 6: 试算方案生成
-- =============================================

DROP PROCEDURE IF EXISTS add_v5_stage6_schedule_plan_columns;

DELIMITER //
CREATE PROCEDURE add_v5_stage6_schedule_plan_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan'
                     AND COLUMN_NAME = 'source_schedule_id') THEN
        ALTER TABLE schedule_plan
            ADD COLUMN source_schedule_id BIGINT NULL COMMENT '试算来源正式课表ID' AFTER source_plan_id;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan'
                     AND COLUMN_NAME = 'repair_task_id') THEN
        ALTER TABLE schedule_plan
            ADD COLUMN repair_task_id BIGINT NULL COMMENT '绑定修复任务ID' AFTER source_schedule_id;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan'
                     AND INDEX_NAME = 'idx_plan_source_schedule') THEN
        CREATE INDEX idx_plan_source_schedule ON schedule_plan(source_schedule_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan'
                     AND INDEX_NAME = 'idx_plan_repair_task') THEN
        CREATE INDEX idx_plan_repair_task ON schedule_plan(repair_task_id);
    END IF;
END //
DELIMITER ;

CALL add_v5_stage6_schedule_plan_columns();
DROP PROCEDURE IF EXISTS add_v5_stage6_schedule_plan_columns;
