-- =============================================
-- V9 auto_schedule_batch 更新时间字段
-- =============================================

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'auto_schedule_batch'
      AND COLUMN_NAME = 'update_time'
);

SET @create_time_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'auto_schedule_batch'
      AND COLUMN_NAME = 'create_time'
);

SET @ddl = IF(@col_exists = 0,
              IF(@create_time_exists > 0,
                 'ALTER TABLE auto_schedule_batch ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER create_time',
                 'ALTER TABLE auto_schedule_batch ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'''),
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

