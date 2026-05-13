-- =============================================
-- V2 schedule 表扩展字段（幂等版本）
-- 添加排课来源和批次关联字段
-- 使用存储过程实现幂等执行，重复执行不会报错
-- =============================================

DROP PROCEDURE IF EXISTS add_schedule_columns_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_schedule_columns_if_not_exists()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule'
                     AND COLUMN_NAME = 'source_type') THEN
        ALTER TABLE schedule
            ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL' COMMENT '排课来源：MANUAL手动 AUTO自动';
    END IF;

    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule'
                     AND COLUMN_NAME = 'batch_id') THEN
        ALTER TABLE schedule
            ADD COLUMN batch_id BIGINT DEFAULT NULL COMMENT '自动排课批次ID，手动排课为空';
    END IF;
END //
DELIMITER ;

CALL add_schedule_columns_if_not_exists();
DROP PROCEDURE IF EXISTS add_schedule_columns_if_not_exists;
