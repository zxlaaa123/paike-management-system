-- N-17: bind exported schedule reports to semester and enable logical delete.

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_report' AND COLUMN_NAME = 'semester_id'),
        'ALTER TABLE schedule_report ADD COLUMN semester_id BIGINT NULL COMMENT ''所属学期ID'' AFTER plan_id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_report' AND COLUMN_NAME = 'deleted'),
        'ALTER TABLE schedule_report ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除'' AFTER updated_at',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_report' AND INDEX_NAME = 'idx_schedule_report_plan_deleted'),
        'ALTER TABLE schedule_report ADD INDEX idx_schedule_report_plan_deleted (plan_id, deleted)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_report' AND INDEX_NAME = 'idx_schedule_report_semester_deleted_created'),
        'ALTER TABLE schedule_report ADD INDEX idx_schedule_report_semester_deleted_created (semester_id, deleted, created_at)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE schedule_report r
JOIN schedule_plan p ON p.id = r.plan_id
SET r.semester_id = p.semester_id
WHERE r.semester_id IS NULL
  AND p.semester_id IS NOT NULL;
