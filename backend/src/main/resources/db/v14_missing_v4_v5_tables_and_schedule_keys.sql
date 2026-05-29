-- C-21/C-22: missing V4/V5 tables and schedule soft-delete-safe unique keys.
-- Use direct CREATE TABLE plus information_schema/PREPARE for idempotent ALTER/INDEX fixes.

CREATE TABLE IF NOT EXISTS schedule_generate_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '生成日志ID',
    plan_id BIGINT NULL COMMENT '排课方案ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    teaching_task_id BIGINT NULL COMMENT '教学任务ID',
    log_level VARCHAR(20) NOT NULL DEFAULT 'INFO' COMMENT '日志级别',
    log_type VARCHAR(50) NOT NULL COMMENT '日志类型',
    message VARCHAR(1000) NOT NULL COMMENT '日志内容',
    step_no INT NULL COMMENT '步骤序号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_generate_log_plan (plan_id),
    KEY idx_generate_log_semester (semester_id),
    KEY idx_generate_log_task (teaching_task_id),
    KEY idx_generate_log_type (log_type)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排课生成日志表';

CREATE TABLE IF NOT EXISTS schedule_unassigned_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '未排任务ID',
    plan_id BIGINT NOT NULL COMMENT '排课方案ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    teaching_task_id BIGINT NOT NULL COMMENT '教学任务ID',
    reason_code VARCHAR(100) NOT NULL COMMENT '未排原因编码',
    reason_message VARCHAR(1000) NOT NULL COMMENT '未排原因说明',
    suggestion VARCHAR(1000) NULL COMMENT '处理建议',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    KEY idx_unassigned_plan (plan_id, deleted),
    KEY idx_unassigned_semester (semester_id, deleted),
    KEY idx_unassigned_task (teaching_task_id, deleted),
    KEY idx_unassigned_reason (reason_code, deleted)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='未排任务原因表';

CREATE TABLE IF NOT EXISTS schedule_adjust_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '调整日志ID',
    plan_id BIGINT NULL COMMENT '排课方案ID',
    schedule_id BIGINT NULL COMMENT '正式课表ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    teaching_task_id BIGINT NOT NULL COMMENT '教学任务ID',
    old_classroom_id BIGINT NULL COMMENT '调整前教室ID',
    old_weekday INT NULL COMMENT '调整前星期',
    old_start_period INT NULL COMMENT '调整前开始节次',
    old_end_period INT NULL COMMENT '调整前结束节次',
    new_classroom_id BIGINT NULL COMMENT '调整后教室ID',
    new_weekday INT NULL COMMENT '调整后星期',
    new_start_period INT NULL COMMENT '调整后开始节次',
    new_end_period INT NULL COMMENT '调整后结束节次',
    before_score DECIMAL(6,2) NULL COMMENT '调整前总分',
    after_score DECIMAL(6,2) NULL COMMENT '调整后总分',
    conflict_flag TINYINT NOT NULL DEFAULT 0 COMMENT '调整后是否冲突',
    adjust_reason VARCHAR(500) NULL COMMENT '调整原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    KEY idx_adjust_plan (plan_id, deleted),
    KEY idx_adjust_schedule (schedule_id, deleted),
    KEY idx_adjust_semester (semester_id, deleted),
    KEY idx_adjust_task (teaching_task_id, deleted)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手动调整日志表';

CREATE TABLE IF NOT EXISTS schedule_locked_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '锁定记录ID',
    target_type VARCHAR(20) NOT NULL COMMENT '锁定目标类型：PLAN/SCHEDULE',
    plan_id BIGINT NULL COMMENT '排课方案ID',
    plan_item_id BIGINT NULL COMMENT '方案明细ID',
    schedule_id BIGINT NULL COMMENT '正式课表ID',
    lock_reason VARCHAR(500) NOT NULL COMMENT '锁定原因',
    active_flag TINYINT NOT NULL DEFAULT 1 COMMENT '是否当前生效',
    active_key BIGINT GENERATED ALWAYS AS (CASE WHEN active_flag = 1 THEN 0 ELSE NULL END) STORED,
    unlocked_at DATETIME NULL COMMENT '取消锁定时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    UNIQUE KEY uk_locked_plan_item (plan_item_id, active_key),
    UNIQUE KEY uk_locked_schedule (schedule_id, active_key),
    KEY idx_locked_plan (plan_id),
    KEY idx_locked_plan_item (plan_item_id),
    KEY idx_locked_schedule (schedule_id),
    KEY idx_locked_active (active_flag)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程锁定记录表';

CREATE TABLE IF NOT EXISTS schedule_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '报告ID',
    plan_id BIGINT NOT NULL COMMENT '排课方案ID',
    semester_id BIGINT NULL COMMENT '所属学期ID',
    report_type VARCHAR(40) NOT NULL COMMENT '报告类型',
    format VARCHAR(20) NOT NULL COMMENT '导出格式',
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATED' COMMENT '生成状态',
    include_charts TINYINT NOT NULL DEFAULT 1 COMMENT '是否包含图表',
    include_risks TINYINT NOT NULL DEFAULT 1 COMMENT '是否包含风险',
    include_suggestions TINYINT NOT NULL DEFAULT 1 COMMENT '是否包含建议',
    file_path VARCHAR(500) NOT NULL COMMENT '报告文件路径',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    KEY idx_schedule_report_plan_deleted (plan_id, deleted),
    KEY idx_schedule_report_semester_deleted_created (semester_id, deleted, created_at),
    KEY idx_schedule_report_type (report_type),
    KEY idx_schedule_report_created (created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V4 排课分析报告表';

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND COLUMN_NAME = 'active_key'
        ),
        'SELECT 1',
        'ALTER TABLE schedule ADD COLUMN active_key BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 0 ELSE NULL END) STORED'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND COLUMN_NAME = 'active_key'
        )
        AND EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND INDEX_NAME = 'uk_schedule_teacher_slot'
              AND COLUMN_NAME = 'deleted'
        ),
        'ALTER TABLE schedule DROP INDEX uk_schedule_teacher_slot',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND COLUMN_NAME = 'active_key'
        )
        AND EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND INDEX_NAME = 'uk_schedule_class_slot'
              AND COLUMN_NAME = 'deleted'
        ),
        'ALTER TABLE schedule DROP INDEX uk_schedule_class_slot',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND COLUMN_NAME = 'active_key'
        )
        AND EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND INDEX_NAME = 'uk_schedule_classroom_slot'
              AND COLUMN_NAME = 'deleted'
        ),
        'ALTER TABLE schedule DROP INDEX uk_schedule_classroom_slot',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND COLUMN_NAME = 'active_key'
        )
        OR EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND INDEX_NAME = 'uk_schedule_teacher_slot'
        ),
        'SELECT 1',
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_teacher_slot (time_slot_id, teacher_id, active_key)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND COLUMN_NAME = 'active_key'
        )
        OR EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND INDEX_NAME = 'uk_schedule_class_slot'
        ),
        'SELECT 1',
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_class_slot (time_slot_id, class_id, active_key)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND COLUMN_NAME = 'active_key'
        )
        OR EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule'
              AND INDEX_NAME = 'uk_schedule_classroom_slot'
        ),
        'SELECT 1',
        'ALTER TABLE schedule ADD UNIQUE KEY uk_schedule_classroom_slot (time_slot_id, classroom_id, active_key)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule_unassigned_task'
              AND COLUMN_NAME = 'deleted'
        ),
        'SELECT 1',
        'ALTER TABLE schedule_unassigned_task ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule_adjust_log'
              AND COLUMN_NAME = 'deleted'
        ),
        'SELECT 1',
        'ALTER TABLE schedule_adjust_log ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'schedule_locked_item'
              AND COLUMN_NAME = 'deleted'
        ),
        'SELECT 1',
        'ALTER TABLE schedule_locked_item ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
