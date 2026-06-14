-- V23: week_type (单双周) support.
--
-- 给 schedule（正式课表）、teaching_task（输入源）加 week_type 列；
-- 重建 schedule 三个软删除安全唯一键 + schedule_plan_item 唯一键与时间索引，
-- 把 week_type 纳入约束维度，使同一时段允许 ODD + EVEN 两条记录共存。
--
-- 幂等：所有 DDL 用 information_schema 探测后决定是否执行，重复运行不报错。
-- week_type DEFAULT 'ALL'：历史数据自动回填为全周，语义等价于 V9 前的场景。
--
-- 配套 V9_00 第 5 节裁决：方案 A（仅单双周 ALL/ODD/EVEN）、存量 ALL 回填可接受。

-- ============================================================
-- 1. schedule 表加 week_type 列
-- ============================================================
SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'week_type'),
        'ALTER TABLE schedule ADD COLUMN week_type VARCHAR(20) NOT NULL DEFAULT ''ALL'' COMMENT ''周次类型：ALL全周、ODD单周、EVEN双周'' AFTER time_slot_id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. schedule 三个软删除安全唯一键重建（纳入 week_type）
--    active_key = CASE WHEN deleted=0 THEN 0 ELSE NULL END，软删除行 active_key 为 NULL 不参与唯一约束。
--    重建后同一 (semester, slot, resource, week_type) 组合在 active 行内唯一；
--    不同 week_type（如 ODD + EVEN）可共存。
-- ============================================================

-- 2.1 teacher 维度：DROP 旧键（不含 week_type）→ ADD 新键（含 week_type）
SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_teacher_slot')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_teacher_slot' AND COLUMN_NAME = 'week_type'),
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
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_teacher_slot (semester_id, time_slot_id, week_type, teacher_id, active_key)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2 class 维度
SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_class_slot')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_class_slot' AND COLUMN_NAME = 'week_type'),
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
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_class_slot (semester_id, time_slot_id, week_type, class_id, active_key)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.3 classroom 维度
SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_classroom_slot')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_classroom_slot' AND COLUMN_NAME = 'week_type'),
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
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_classroom_slot (semester_id, time_slot_id, week_type, classroom_id, active_key)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. teaching_task 表加 week_type 列（输入源）
-- ============================================================
SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teaching_task' AND COLUMN_NAME = 'week_type'),
        'ALTER TABLE teaching_task ADD COLUMN week_type VARCHAR(20) NOT NULL DEFAULT ''ALL'' COMMENT ''周次类型：ALL全周、ODD单周、EVEN双周'' AFTER weekly_hours',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 4. schedule_plan_item 唯一键 uk_plan_task_slot 重建（纳入 week_type）
--    重建后同一任务同一时段允许 ODD + EVEN 两条 plan_item 共存。
-- ============================================================
SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'uk_plan_task_slot')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'uk_plan_task_slot' AND COLUMN_NAME = 'week_type'),
        'ALTER TABLE schedule_plan_item DROP INDEX uk_plan_task_slot',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'uk_plan_task_slot'),
        'ALTER TABLE schedule_plan_item ADD UNIQUE KEY uk_plan_task_slot (plan_id, teaching_task_id, weekday, start_period, end_period, week_type)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 5. schedule_plan_item 三个时间索引重建（纳入 week_type）
--    冲突检测性能依赖这些索引；单双周下需按 week_type 区分。
-- ============================================================

-- 5.1 teacher_time
SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'idx_plan_item_teacher_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'idx_plan_item_teacher_time' AND COLUMN_NAME = 'week_type'),
        'ALTER TABLE schedule_plan_item DROP INDEX idx_plan_item_teacher_time',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'idx_plan_item_teacher_time'),
        'ALTER TABLE schedule_plan_item ADD INDEX idx_plan_item_teacher_time (teacher_id, weekday, start_period, end_period, week_type)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5.2 class_time
SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'idx_plan_item_class_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'idx_plan_item_class_time' AND COLUMN_NAME = 'week_type'),
        'ALTER TABLE schedule_plan_item DROP INDEX idx_plan_item_class_time',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'idx_plan_item_class_time'),
        'ALTER TABLE schedule_plan_item ADD INDEX idx_plan_item_class_time (class_id, weekday, start_period, end_period, week_type)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5.3 room_time
SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'idx_plan_item_room_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'idx_plan_item_room_time' AND COLUMN_NAME = 'week_type'),
        'ALTER TABLE schedule_plan_item DROP INDEX idx_plan_item_room_time',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND INDEX_NAME = 'idx_plan_item_room_time'),
        'ALTER TABLE schedule_plan_item ADD INDEX idx_plan_item_room_time (classroom_id, weekday, start_period, end_period, week_type)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
