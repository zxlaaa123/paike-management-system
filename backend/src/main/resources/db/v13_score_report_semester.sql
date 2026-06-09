-- C-11: bind score reports to semester so latest/list/generate do not mix terms.

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_score_report' AND COLUMN_NAME = 'semester_id'),
        'ALTER TABLE schedule_score_report ADD COLUMN semester_id BIGINT NULL COMMENT ''所属学期ID'' AFTER id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_score_report' AND INDEX_NAME = 'idx_score_report_semester'),
        'ALTER TABLE schedule_score_report ADD INDEX idx_score_report_semester (semester_id, create_time)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE schedule_score_report
SET semester_id = (SELECT id FROM semester WHERE is_current = 1 ORDER BY updated_at DESC, id DESC LIMIT 1)
WHERE semester_id IS NULL
  AND EXISTS (SELECT 1 FROM semester WHERE is_current = 1 LIMIT 1);
