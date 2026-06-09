-- =============================================
-- V16 schedule list search/order index
-- M-08: keep contains-search semantics, optimize default semester list ordering.
-- =============================================

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'idx_schedule_semester_deleted_created'),
        'ALTER TABLE schedule ADD KEY idx_schedule_semester_deleted_created (semester_id, deleted, create_time, id)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
