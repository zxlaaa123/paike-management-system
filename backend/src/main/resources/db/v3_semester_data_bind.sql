-- =============================================
-- V3 数据库迁移脚本 - 核心数据绑定学期
-- 为 teaching_task 和 schedule 表增加 semester_id
-- 注意：此脚本使用 continue-on-error 模式，重复执行不会导致启动失败
-- =============================================

-- -----------------------------------
-- 1. teaching_task 增加 semester_id
-- -----------------------------------
ALTER TABLE teaching_task
ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id;

-- -----------------------------------
-- 2. schedule 增加 semester_id 和 plan_id
-- -----------------------------------
ALTER TABLE schedule
ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id,
ADD COLUMN plan_id BIGINT NULL COMMENT '来源排课方案ID' AFTER batch_id;

-- -----------------------------------
-- 3. 旧数据迁移到当前学期
-- -----------------------------------
UPDATE teaching_task tt
SET tt.semester_id = (SELECT id FROM semester WHERE is_current = 1 LIMIT 1)
WHERE tt.semester_id IS NULL;

UPDATE schedule s
SET s.semester_id = (SELECT id FROM semester WHERE is_current = 1 LIMIT 1)
WHERE s.semester_id IS NULL;
