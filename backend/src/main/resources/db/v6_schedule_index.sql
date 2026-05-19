-- =============================================
-- V6 schedule 表业务索引和唯一约束（幂等版本）
-- 修复 P0: TOCTOU 竞态 + schedule 表零索引（#1 + A4）
-- 唯一约束把 deleted 纳入键，软删一条后允许同位置再插
-- =============================================

DROP PROCEDURE IF EXISTS add_schedule_indexes_v6;

DELIMITER //
CREATE PROCEDURE add_schedule_indexes_v6()
BEGIN
    -- 普通索引（加速冲突检测 / 列表查询 / 统计）
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule'
                     AND INDEX_NAME = 'idx_schedule_time_slot') THEN
        ALTER TABLE schedule ADD KEY idx_schedule_time_slot (time_slot_id, deleted);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule'
                     AND INDEX_NAME = 'idx_schedule_teacher') THEN
        ALTER TABLE schedule ADD KEY idx_schedule_teacher (teacher_id, deleted);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule'
                     AND INDEX_NAME = 'idx_schedule_class') THEN
        ALTER TABLE schedule ADD KEY idx_schedule_class (class_id, deleted);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule'
                     AND INDEX_NAME = 'idx_schedule_classroom') THEN
        ALTER TABLE schedule ADD KEY idx_schedule_classroom (classroom_id, deleted);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule'
                     AND INDEX_NAME = 'idx_schedule_task') THEN
        ALTER TABLE schedule ADD KEY idx_schedule_task (teaching_task_id, deleted);
    END IF;

    -- 唯一约束 — 兜底 ScheduleConflictService 的 TOCTOU 双写
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule'
                     AND INDEX_NAME = 'uk_schedule_teacher_slot') THEN
        ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_teacher_slot (time_slot_id, teacher_id, deleted);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule'
                     AND INDEX_NAME = 'uk_schedule_class_slot') THEN
        ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_class_slot (time_slot_id, class_id, deleted);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule'
                     AND INDEX_NAME = 'uk_schedule_classroom_slot') THEN
        ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_classroom_slot (time_slot_id, classroom_id, deleted);
    END IF;
END //
DELIMITER ;

CALL add_schedule_indexes_v6();
DROP PROCEDURE IF EXISTS add_schedule_indexes_v6;
