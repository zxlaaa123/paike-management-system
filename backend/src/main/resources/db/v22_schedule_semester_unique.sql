-- V22: scope active schedule unique keys by semester.
--
-- V6 introduced soft-delete-safe active_key unique keys for schedule rows, but the
-- keys did not include semester_id. That made the same teacher/class/classroom at
-- the same time slot conflict across different semesters. Keep the historical
-- key names so later code/tests can keep checking the same business constraints.

SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_teacher_slot') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_teacher_slot' AND COLUMN_NAME = 'semester_id'),
        'ALTER TABLE schedule DROP INDEX uk_schedule_teacher_slot',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_class_slot') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_class_slot' AND COLUMN_NAME = 'semester_id'),
        'ALTER TABLE schedule DROP INDEX uk_schedule_class_slot',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_classroom_slot') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_classroom_slot' AND COLUMN_NAME = 'semester_id'),
        'ALTER TABLE schedule DROP INDEX uk_schedule_classroom_slot',
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
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_schedule_classroom_slot'),
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_classroom_slot (semester_id, time_slot_id, classroom_id, active_key)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

