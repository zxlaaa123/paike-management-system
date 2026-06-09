-- =============================================
-- V6 schedule 表业务索引和唯一约束（幂等版本）
-- 修复 P0: TOCTOU 竞态 + schedule 表零索引（#1 + A4）
-- 唯一约束使用 semester_id + active_key：active rows 固定为 0，soft-deleted rows 为 NULL。
-- MySQL UNIQUE 允许多个 NULL，支持同位置多次软删后重建。
-- =============================================

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'active_key'),
        'ALTER TABLE schedule ADD COLUMN active_key BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 0 ELSE NULL END) STORED',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

    -- 普通索引（加速冲突检测 / 列表查询 / 统计）
SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'idx_schedule_time_slot'),
        'ALTER TABLE schedule ADD KEY idx_schedule_time_slot (time_slot_id, deleted)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'idx_schedule_teacher'),
        'ALTER TABLE schedule ADD KEY idx_schedule_teacher (teacher_id, deleted)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'idx_schedule_class'),
        'ALTER TABLE schedule ADD KEY idx_schedule_class (class_id, deleted)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'idx_schedule_classroom'),
        'ALTER TABLE schedule ADD KEY idx_schedule_classroom (classroom_id, deleted)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'idx_schedule_semester'),
        'ALTER TABLE schedule ADD KEY idx_schedule_semester (semester_id, deleted)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'idx_schedule_task'),
        'ALTER TABLE schedule ADD KEY idx_schedule_task (teaching_task_id, deleted)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

    -- 唯一约束 — 兜底 ScheduleConflictService 的 TOCTOU 双写
SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_teacher_slot' AND COLUMN_NAME = 'deleted'),
        'ALTER TABLE schedule DROP INDEX uk_schedule_teacher_slot',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_teacher_slot'),
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_teacher_slot (semester_id, time_slot_id, teacher_id, active_key)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_class_slot' AND COLUMN_NAME = 'deleted'),
        'ALTER TABLE schedule DROP INDEX uk_schedule_class_slot',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_class_slot'),
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_class_slot (semester_id, time_slot_id, class_id, active_key)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_classroom_slot' AND COLUMN_NAME = 'deleted'),
        'ALTER TABLE schedule DROP INDEX uk_schedule_classroom_slot',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_classroom_slot'),
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_classroom_slot (semester_id, time_slot_id, classroom_id, active_key)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
