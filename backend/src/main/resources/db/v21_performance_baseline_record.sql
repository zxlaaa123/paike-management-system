CREATE TABLE IF NOT EXISTS performance_baseline_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '性能基线记录ID',
    operation_type VARCHAR(64) NOT NULL COMMENT '操作类型',
    semester_id BIGINT NULL COMMENT '学期ID',
    plan_id BIGINT NULL COMMENT '方案ID',
    target_id BIGINT NULL COMMENT '目标对象ID',
    task_count INT NULL COMMENT '任务数量',
    schedule_count INT NULL COMMENT '排课记录数量',
    duration_ms BIGINT NOT NULL COMMENT '耗时毫秒',
    success TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功 0=失败 1=成功',
    error_code VARCHAR(64) NULL COMMENT '错误码',
    error_message VARCHAR(1000) NULL COMMENT '错误信息',
    extra_json TEXT NULL COMMENT '附加信息JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_perf_operation (operation_type),
    INDEX idx_perf_semester (semester_id),
    INDEX idx_perf_plan (plan_id),
    INDEX idx_perf_success (success),
    INDEX idx_perf_created (created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V6 性能基线记录';

