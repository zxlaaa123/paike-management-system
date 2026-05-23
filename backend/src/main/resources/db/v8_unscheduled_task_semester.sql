-- =============================================
-- V8 unscheduled_task 学期隔离字段
-- =============================================

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'unscheduled_task'
      AND COLUMN_NAME = 'semester_id'
);

SET @ddl = IF(@col_exists = 0,
              'ALTER TABLE unscheduled_task ADD COLUMN semester_id BIGINT NULL AFTER batch_id',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE unscheduled_task ut
JOIN teaching_task tt ON tt.id = ut.task_id
SET ut.semester_id = tt.semester_id
WHERE ut.semester_id IS NULL
  AND tt.semester_id IS NOT NULL;

DELETE FROM unscheduled_task
WHERE semester_id IS NULL;

SET @semester_nullable = (
    SELECT IS_NULLABLE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'unscheduled_task'
      AND COLUMN_NAME = 'semester_id'
);

SET @ddl = IF(@semester_nullable = 'YES',
              'ALTER TABLE unscheduled_task MODIFY COLUMN semester_id BIGINT NOT NULL DEFAULT 0 COMMENT ''所属学期ID''',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'unscheduled_task'
      AND INDEX_NAME = 'idx_unscheduled_task_semester'
);

SET @ddl = IF(@idx_exists = 0,
              'CREATE INDEX idx_unscheduled_task_semester ON unscheduled_task (semester_id)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
