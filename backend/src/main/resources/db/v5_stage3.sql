-- =============================================
-- V5 Stage 3: 修复任务管理扩展
-- =============================================

DROP PROCEDURE IF EXISTS add_v5_stage3_repair_task_columns;

DELIMITER //
CREATE PROCEDURE add_v5_stage3_repair_task_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_repair_task'
                     AND COLUMN_NAME = 'title') THEN
        ALTER TABLE schedule_repair_task
            ADD COLUMN title VARCHAR(200) NULL COMMENT '修复任务标题' AFTER task_code;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_repair_task'
                     AND COLUMN_NAME = 'risk_item_ids') THEN
        ALTER TABLE schedule_repair_task
            ADD COLUMN risk_item_ids TEXT NULL COMMENT '关联风险项ID列表(JSON)' AFTER risk_types;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_repair_task'
                     AND COLUMN_NAME = 'scope_plan_item_ids') THEN
        ALTER TABLE schedule_repair_task
            ADD COLUMN scope_plan_item_ids TEXT NULL COMMENT '修复范围方案明细ID列表(JSON)' AFTER risk_item_ids;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_repair_task'
                     AND COLUMN_NAME = 'cancel_reason') THEN
        ALTER TABLE schedule_repair_task
            ADD COLUMN cancel_reason VARCHAR(500) NULL COMMENT '取消原因' AFTER error_message;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_repair_task'
                     AND INDEX_NAME = 'idx_repair_task_result_plan') THEN
        CREATE INDEX idx_repair_task_result_plan ON schedule_repair_task(result_plan_id);
    END IF;
END //
DELIMITER ;

CALL add_v5_stage3_repair_task_columns();
DROP PROCEDURE IF EXISTS add_v5_stage3_repair_task_columns;
