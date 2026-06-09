-- N-16: bind conflict reports and auto schedule batches to semester.

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_conflict_report' AND COLUMN_NAME = 'semester_id'),
        'ALTER TABLE schedule_conflict_report ADD COLUMN semester_id BIGINT NULL COMMENT ''所属学期ID'' AFTER id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_conflict_report' AND INDEX_NAME = 'idx_conflict_report_semester'),
        'ALTER TABLE schedule_conflict_report ADD INDEX idx_conflict_report_semester (semester_id, create_time)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auto_schedule_batch' AND COLUMN_NAME = 'semester_id'),
        'ALTER TABLE auto_schedule_batch ADD COLUMN semester_id BIGINT NULL COMMENT ''所属学期ID'' AFTER id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auto_schedule_batch' AND INDEX_NAME = 'idx_auto_schedule_batch_semester'),
        'ALTER TABLE auto_schedule_batch ADD INDEX idx_auto_schedule_batch_semester (semester_id, create_time)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE schedule_conflict_report
SET semester_id = (SELECT id FROM semester WHERE is_current = 1 ORDER BY updated_at DESC, id DESC LIMIT 1)
WHERE semester_id IS NULL
  AND EXISTS (SELECT 1 FROM semester WHERE is_current = 1 LIMIT 1);

UPDATE auto_schedule_batch
SET semester_id = (SELECT id FROM semester WHERE is_current = 1 ORDER BY updated_at DESC, id DESC LIMIT 1)
WHERE semester_id IS NULL
  AND EXISTS (SELECT 1 FROM semester WHERE is_current = 1 LIMIT 1);
