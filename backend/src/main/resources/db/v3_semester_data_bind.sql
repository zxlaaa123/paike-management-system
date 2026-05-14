-- =============================================
-- V3 数据库迁移脚本 - 核心数据绑定学期
-- 为 teaching_task 和 schedule 表增加 semester_id
-- 使用存储过程确保幂等性（mode: always 每次启动都执行）
-- =============================================

-- -----------------------------------
-- 1. teaching_task 增加 semester_id（如果不存在）
-- -----------------------------------
DROP PROCEDURE IF EXISTS add_semester_id_to_teaching_task;
DELIMITER $$
CREATE PROCEDURE add_semester_id_to_teaching_task()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'teaching_task'
          AND COLUMN_NAME = 'semester_id'
    ) THEN
        ALTER TABLE teaching_task
        ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id;
    END IF;
END$$
DELIMITER ;
CALL add_semester_id_to_teaching_task();
DROP PROCEDURE IF EXISTS add_semester_id_to_teaching_task;

-- -----------------------------------
-- 2. schedule 增加 semester_id 和 plan_id（如果不存在）
-- -----------------------------------
DROP PROCEDURE IF EXISTS add_semester_fields_to_schedule;
DELIMITER $$
CREATE PROCEDURE add_semester_fields_to_schedule()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'schedule'
          AND COLUMN_NAME = 'semester_id'
    ) THEN
        ALTER TABLE schedule
        ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'schedule'
          AND COLUMN_NAME = 'plan_id'
    ) THEN
        ALTER TABLE schedule
        ADD COLUMN plan_id BIGINT NULL COMMENT '来源排课方案ID' AFTER batch_id;
    END IF;
END$$
DELIMITER ;
CALL add_semester_fields_to_schedule();
DROP PROCEDURE IF EXISTS add_semester_fields_to_schedule;

-- -----------------------------------
-- 3. 旧数据迁移到当前学期
-- 将 semester_id 为空的记录绑定到当前学期
-- -----------------------------------
UPDATE teaching_task tt
SET tt.semester_id = (SELECT id FROM semester WHERE is_current = 1 LIMIT 1)
WHERE tt.semester_id IS NULL;

UPDATE schedule s
SET s.semester_id = (SELECT id FROM semester WHERE is_current = 1 LIMIT 1)
WHERE s.semester_id IS NULL;
