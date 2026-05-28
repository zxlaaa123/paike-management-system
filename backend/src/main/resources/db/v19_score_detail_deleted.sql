-- N-19: enable logical delete for schedule score details.

DROP PROCEDURE IF EXISTS add_score_detail_deleted_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_score_detail_deleted_if_not_exists()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule_score_detail'
                     AND COLUMN_NAME = 'deleted') THEN
        ALTER TABLE schedule_score_detail
            ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除' AFTER created_at;
    END IF;

    IF NOT EXISTS (SELECT * FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule_score_detail'
                     AND INDEX_NAME = 'idx_score_detail_plan_deleted') THEN
        ALTER TABLE schedule_score_detail
            ADD INDEX idx_score_detail_plan_deleted (plan_id, deleted);
    END IF;

    IF NOT EXISTS (SELECT * FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule_score_detail'
                     AND INDEX_NAME = 'idx_score_detail_semester_deleted') THEN
        ALTER TABLE schedule_score_detail
            ADD INDEX idx_score_detail_semester_deleted (semester_id, deleted);
    END IF;
END //
DELIMITER ;

CALL add_score_detail_deleted_if_not_exists();
DROP PROCEDURE IF EXISTS add_score_detail_deleted_if_not_exists;
