-- =============================================
-- V7 soft delete for schedule_plan / schedule_plan_item / semester
-- Spring sql.init 直接执行分号分隔语句，改用 information_schema + PREPARE 做幂等 ALTER。
-- =============================================

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule_plan'
              AND COLUMN_NAME = 'deleted'
        ),
        'SELECT 1',
        'ALTER TABLE schedule_plan ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule_plan_item'
              AND COLUMN_NAME = 'deleted'
        ),
        'SELECT 1',
        'ALTER TABLE schedule_plan_item ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'semester'
              AND COLUMN_NAME = 'deleted'
        ),
        'SELECT 1',
        'ALTER TABLE semester ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
