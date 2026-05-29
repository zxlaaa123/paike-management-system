-- M-35: widen conflict report related schedule id list.

DROP PROCEDURE IF EXISTS widen_conflict_related_schedule_ids;

DELIMITER //
CREATE PROCEDURE widen_conflict_related_schedule_ids()
BEGIN
    IF EXISTS (SELECT * FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'schedule_conflict_report'
                 AND COLUMN_NAME = 'related_schedule_ids'
                 AND DATA_TYPE <> 'text') THEN
        ALTER TABLE schedule_conflict_report
            MODIFY COLUMN related_schedule_ids TEXT NULL COMMENT '相关排课记录ID';
    END IF;
END //
DELIMITER ;

CALL widen_conflict_related_schedule_ids();
DROP PROCEDURE IF EXISTS widen_conflict_related_schedule_ids;

