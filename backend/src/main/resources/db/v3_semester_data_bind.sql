-- =============================================
-- V3 数据库迁移脚本 - 核心数据绑定学期
-- 为 teaching_task 和 schedule 表增加 semester_id
-- =============================================

-- -----------------------------------
-- 1. teaching_task 增加 semester_id
-- -----------------------------------
DROP PROCEDURE IF EXISTS add_semester_data_bind_columns_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_semester_data_bind_columns_if_not_exists()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'teaching_task'
                     AND COLUMN_NAME = 'semester_id') THEN
        ALTER TABLE teaching_task
            ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id;
    END IF;

-- -----------------------------------
-- 2. schedule 增加 semester_id 和 plan_id
-- -----------------------------------
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule'
                     AND COLUMN_NAME = 'semester_id') THEN
        ALTER TABLE schedule
            ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id;
    END IF;

    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule'
                     AND COLUMN_NAME = 'plan_id') THEN
        ALTER TABLE schedule
            ADD COLUMN plan_id BIGINT NULL COMMENT '来源排课方案ID' AFTER batch_id;
    END IF;
END //
DELIMITER ;

CALL add_semester_data_bind_columns_if_not_exists();
DROP PROCEDURE IF EXISTS add_semester_data_bind_columns_if_not_exists;

-- -----------------------------------
-- 3. 旧数据迁移到当前学期（仅当存在当前学期时执行）
-- -----------------------------------
UPDATE teaching_task tt
SET tt.semester_id = (SELECT id FROM semester WHERE is_current = 1 LIMIT 1)
WHERE tt.semester_id IS NULL
  AND EXISTS (SELECT 1 FROM semester WHERE is_current = 1 LIMIT 1);

UPDATE schedule s
SET s.semester_id = (SELECT id FROM semester WHERE is_current = 1 LIMIT 1)
WHERE s.semester_id IS NULL
  AND EXISTS (SELECT 1 FROM semester WHERE is_current = 1 LIMIT 1);
