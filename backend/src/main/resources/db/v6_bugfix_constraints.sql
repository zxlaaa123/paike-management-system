-- =============================================
-- V6 bugfix constraints
-- - teacher_unavailable_time: allow repeated soft-delete/recreate cycles
-- - schedule_plan_item: prevent duplicate slot rows for the same task in one plan
-- =============================================

DROP PROCEDURE IF EXISTS add_bugfix_constraints_v6;

DELIMITER //
CREATE PROCEDURE add_bugfix_constraints_v6()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_unavailable_time'
                     AND COLUMN_NAME = 'active_key') THEN
        ALTER TABLE teacher_unavailable_time
            ADD COLUMN active_key BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 0 ELSE NULL END) STORED;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_unavailable_time'
                 AND INDEX_NAME = 'uk_teacher_timeslot' AND COLUMN_NAME = 'deleted') THEN
        ALTER TABLE teacher_unavailable_time DROP INDEX uk_teacher_timeslot;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_unavailable_time'
                     AND INDEX_NAME = 'uk_teacher_timeslot') THEN
        ALTER TABLE teacher_unavailable_time
            ADD UNIQUE KEY uk_teacher_timeslot (teacher_id, time_slot_id, active_key);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item'
                     AND INDEX_NAME = 'uk_plan_task_slot') THEN
        ALTER TABLE schedule_plan_item
            ADD UNIQUE KEY uk_plan_task_slot (plan_id, teaching_task_id, weekday, start_period, end_period);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_locked_item'
                     AND COLUMN_NAME = 'active_key') THEN
        ALTER TABLE schedule_locked_item
            ADD COLUMN active_key BIGINT GENERATED ALWAYS AS (CASE WHEN active_flag = 1 THEN 0 ELSE NULL END) STORED;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_locked_item'
                     AND INDEX_NAME = 'uk_locked_plan_item') THEN
        ALTER TABLE schedule_locked_item
            ADD UNIQUE KEY uk_locked_plan_item (plan_item_id, active_key);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_locked_item'
                     AND INDEX_NAME = 'uk_locked_schedule') THEN
        ALTER TABLE schedule_locked_item
            ADD UNIQUE KEY uk_locked_schedule (schedule_id, active_key);
    END IF;
END //
DELIMITER ;

CALL add_bugfix_constraints_v6();
DROP PROCEDURE IF EXISTS add_bugfix_constraints_v6;
