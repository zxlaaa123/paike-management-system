-- =============================================
-- V10 teaching_task 常用查询索引
-- =============================================

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'teaching_task'
      AND INDEX_NAME = 'idx_tt_course'
);

SET @ddl = IF(@idx_exists = 0,
              'CREATE INDEX idx_tt_course ON teaching_task (course_id)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'teaching_task'
      AND INDEX_NAME = 'idx_tt_teacher'
);

SET @ddl = IF(@idx_exists = 0,
              'CREATE INDEX idx_tt_teacher ON teaching_task (teacher_id)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'teaching_task'
      AND INDEX_NAME = 'idx_tt_class'
);

SET @ddl = IF(@idx_exists = 0,
              'CREATE INDEX idx_tt_class ON teaching_task (class_id)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @semester_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'teaching_task'
      AND COLUMN_NAME = 'semester_id'
);

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'teaching_task'
      AND INDEX_NAME = 'idx_tt_semester'
);

SET @ddl = IF(@semester_col_exists > 0 AND @idx_exists = 0,
              'CREATE INDEX idx_tt_semester ON teaching_task (semester_id)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

