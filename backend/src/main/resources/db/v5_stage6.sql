-- =============================================
-- V5 Stage 6: 试算方案生成
-- =============================================

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan' AND COLUMN_NAME = 'source_schedule_id'),
        'ALTER TABLE schedule_plan ADD COLUMN source_schedule_id BIGINT NULL COMMENT ''试算来源正式课表ID'' AFTER source_plan_id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan' AND COLUMN_NAME = 'repair_task_id'),
        'ALTER TABLE schedule_plan ADD COLUMN repair_task_id BIGINT NULL COMMENT ''绑定修复任务ID'' AFTER source_schedule_id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan' AND INDEX_NAME = 'idx_plan_source_schedule'),
        'CREATE INDEX idx_plan_source_schedule ON schedule_plan(source_schedule_id)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan' AND INDEX_NAME = 'idx_plan_repair_task'),
        'CREATE INDEX idx_plan_repair_task ON schedule_plan(repair_task_id)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
