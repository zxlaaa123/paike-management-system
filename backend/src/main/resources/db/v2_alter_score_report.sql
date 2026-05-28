-- =============================================
-- 为 schedule_score_report 表添加 grade_name 字段
-- =============================================
DROP PROCEDURE IF EXISTS add_score_report_grade_name_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_score_report_grade_name_if_not_exists()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule_score_report'
                     AND COLUMN_NAME = 'grade_name') THEN
        ALTER TABLE schedule_score_report
            ADD COLUMN grade_name VARCHAR(20) DEFAULT NULL COMMENT '等级名称：优秀/良好/一般/较差/需要调整' AFTER grade;
    END IF;
END //
DELIMITER ;

CALL add_score_report_grade_name_if_not_exists();
DROP PROCEDURE IF EXISTS add_score_report_grade_name_if_not_exists;
