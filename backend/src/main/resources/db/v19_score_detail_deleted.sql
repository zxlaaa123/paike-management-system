-- N-19: enable logical delete for schedule score details.

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_score_detail' AND COLUMN_NAME = 'deleted'),
        'ALTER TABLE schedule_score_detail ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除'' AFTER created_at',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_score_detail' AND INDEX_NAME = 'idx_score_detail_plan_deleted'),
        'ALTER TABLE schedule_score_detail ADD INDEX idx_score_detail_plan_deleted (plan_id, deleted)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT * FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_score_detail' AND INDEX_NAME = 'idx_score_detail_semester_deleted'),
        'ALTER TABLE schedule_score_detail ADD INDEX idx_score_detail_semester_deleted (semester_id, deleted)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
