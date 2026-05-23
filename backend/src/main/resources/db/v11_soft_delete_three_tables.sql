-- =============================================
-- V11 三张关键业务表软删除补齐
-- =============================================

-- schedule_locked_item.deleted
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'schedule_locked_item'
      AND COLUMN_NAME = 'deleted'
);

SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE schedule_locked_item ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''软删除标记 0=未删 1=已删''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- schedule_adjust_log.deleted
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'schedule_adjust_log'
      AND COLUMN_NAME = 'deleted'
);

SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE schedule_adjust_log ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''软删除标记 0=未删 1=已删''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- schedule_unassigned_task.deleted
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'schedule_unassigned_task'
      AND COLUMN_NAME = 'deleted'
);

SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE schedule_unassigned_task ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''软删除标记 0=未删 1=已删''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
