-- =============================================
-- 为 schedule_score_report 表添加 grade_name 字段
-- =============================================
ALTER TABLE schedule_score_report ADD COLUMN grade_name VARCHAR(20) DEFAULT NULL COMMENT '等级名称：优秀/良好/一般/较差/需要调整' AFTER grade;
