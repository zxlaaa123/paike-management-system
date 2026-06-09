-- M-35: widen conflict report related schedule id list.

SET @ddl = (
    SELECT IF(
        EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_conflict_report' AND COLUMN_NAME = 'related_schedule_ids' AND DATA_TYPE <> 'text'),
        'ALTER TABLE schedule_conflict_report MODIFY COLUMN related_schedule_ids TEXT NULL COMMENT ''相关排课记录ID''',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

