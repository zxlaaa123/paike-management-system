-- =============================================
-- V16 schedule list search/order index
-- M-08: keep contains-search semantics, optimize default semester list ordering.
-- =============================================

DROP PROCEDURE IF EXISTS add_schedule_search_order_index_v16;

DELIMITER //
CREATE PROCEDURE add_schedule_search_order_index_v16()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule'
                     AND INDEX_NAME = 'idx_schedule_semester_deleted_created') THEN
        ALTER TABLE schedule ADD KEY idx_schedule_semester_deleted_created (semester_id, deleted, create_time, id);
    END IF;
END //
DELIMITER ;

CALL add_schedule_search_order_index_v16();
DROP PROCEDURE IF EXISTS add_schedule_search_order_index_v16;
