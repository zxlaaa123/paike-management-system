-- =============================================
-- 为 schedule_score_report 表添加 grade_name 字段
-- =============================================

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_score_report' AND COLUMN_NAME = 'grade_name'),
        'ALTER TABLE schedule_score_report ADD COLUMN grade_name VARCHAR(20) DEFAULT NULL COMMENT ''等级名称：优秀/良好/一般/较差/需要调整'' AFTER grade',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
