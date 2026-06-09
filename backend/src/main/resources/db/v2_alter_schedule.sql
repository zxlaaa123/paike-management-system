-- =============================================
-- V2 schedule 表扩展字段（幂等版本）
-- 添加排课来源和批次关联字段
-- 使用 information_schema + PREPARE 实现幂等执行，重复执行不会报错
-- =============================================

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'source_type'),
        'ALTER TABLE schedule ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT ''MANUAL'' COMMENT ''排课来源：MANUAL手动 AUTO自动''',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'batch_id'),
        'ALTER TABLE schedule ADD COLUMN batch_id BIGINT DEFAULT NULL COMMENT ''自动排课批次ID，手动排课为空''',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
