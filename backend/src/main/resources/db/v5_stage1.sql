-- =============================================
-- V5 Stage 1: 数据库扩展与实体准备
-- =============================================

-- 0) 扩展 schedule_plan：标记试算方案与来源方案
ALTER TABLE schedule_plan
    ADD COLUMN plan_mode VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '方案模式：NORMAL/SIMULATION' AFTER strategy_type;

ALTER TABLE schedule_plan
    ADD COLUMN source_plan_id BIGINT NULL COMMENT '来源方案ID（试算/重排来源）' AFTER id;

CREATE INDEX idx_schedule_plan_mode ON schedule_plan(plan_mode);
CREATE INDEX idx_schedule_plan_source_plan ON schedule_plan(source_plan_id);

-- 1) 修复任务记录
CREATE TABLE IF NOT EXISTS schedule_repair_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '修复任务ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    plan_id BIGINT NOT NULL COMMENT '目标方案ID',
    source_plan_id BIGINT NULL COMMENT '来源方案ID',
    source_schedule_id BIGINT NULL COMMENT '来源正式课表记录ID',
    task_code VARCHAR(64) NOT NULL COMMENT '任务编码',
    task_type VARCHAR(32) NOT NULL COMMENT '任务类型：RISK_REPAIR/LOCAL_REPLAN/SMART_FIX',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    trigger_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '触发来源：MANUAL/SYSTEM',
    risk_types VARCHAR(255) NULL COMMENT '风险类型列表（逗号分隔）',
    target_item_count INT NOT NULL DEFAULT 0 COMMENT '目标课程数',
    locked_item_count INT NOT NULL DEFAULT 0 COMMENT '锁定课程数',
    processed_item_count INT NOT NULL DEFAULT 0 COMMENT '已处理课程数',
    success_item_count INT NOT NULL DEFAULT 0 COMMENT '成功修复课程数',
    failure_item_count INT NOT NULL DEFAULT 0 COMMENT '失败课程数',
    result_plan_id BIGINT NULL COMMENT '输出方案ID（试算或新方案）',
    started_at DATETIME NULL COMMENT '开始时间',
    finished_at DATETIME NULL COMMENT '结束时间',
    error_message VARCHAR(1000) NULL COMMENT '失败信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_repair_task_semester (semester_id),
    INDEX idx_repair_task_plan (plan_id),
    INDEX idx_repair_task_source_plan (source_plan_id),
    INDEX idx_repair_task_source_schedule (source_schedule_id),
    INDEX idx_repair_task_status (status),
    INDEX idx_repair_task_created (created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V5 修复任务记录';

-- 2) 修复建议记录
CREATE TABLE IF NOT EXISTS schedule_repair_suggestion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '修复建议ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    plan_id BIGINT NOT NULL COMMENT '所属方案ID',
    repair_task_id BIGINT NOT NULL COMMENT '关联修复任务ID',
    source_plan_id BIGINT NULL COMMENT '来源方案ID',
    source_schedule_id BIGINT NULL COMMENT '来源正式课表ID',
    source_plan_item_id BIGINT NULL COMMENT '来源方案明细ID',
    suggestion_code VARCHAR(64) NOT NULL COMMENT '建议编码',
    suggestion_type VARCHAR(32) NOT NULL COMMENT '建议类型：MOVE_TIME/CHANGE_ROOM/SWAP/DEFER',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '建议状态',
    priority_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：LOW/MEDIUM/HIGH',
    expected_score_delta DECIMAL(8,2) NULL COMMENT '预期分数变化',
    expected_risk_delta INT NULL COMMENT '预期风险变化',
    expected_unscheduled_delta INT NULL COMMENT '预期未排变化',
    reason_summary VARCHAR(500) NULL COMMENT '建议摘要',
    detail_json TEXT NULL COMMENT '建议详情JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_repair_suggestion_semester (semester_id),
    INDEX idx_repair_suggestion_plan (plan_id),
    INDEX idx_repair_suggestion_task (repair_task_id),
    INDEX idx_repair_suggestion_status (status),
    INDEX idx_repair_suggestion_created (created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V5 修复建议记录';

-- 3) 候选位置记录
CREATE TABLE IF NOT EXISTS schedule_candidate_position (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '候选位置ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    plan_id BIGINT NOT NULL COMMENT '所属方案ID',
    repair_task_id BIGINT NULL COMMENT '关联修复任务ID',
    suggestion_id BIGINT NULL COMMENT '关联建议ID',
    source_plan_id BIGINT NULL COMMENT '来源方案ID',
    source_schedule_id BIGINT NULL COMMENT '来源正式课表ID',
    plan_item_id BIGINT NULL COMMENT '目标方案明细ID',
    teaching_task_id BIGINT NOT NULL COMMENT '教学任务ID',
    candidate_weekday INT NOT NULL COMMENT '候选星期',
    candidate_start_period INT NOT NULL COMMENT '候选开始节次',
    candidate_end_period INT NOT NULL COMMENT '候选结束节次',
    candidate_classroom_id BIGINT NOT NULL COMMENT '候选教室ID',
    candidate_time_slot_id BIGINT NULL COMMENT '候选时段ID',
    candidate_score DECIMAL(8,2) NULL COMMENT '候选评分',
    hard_conflict_count INT NOT NULL DEFAULT 0 COMMENT '硬冲突数',
    soft_penalty_score DECIMAL(8,2) NULL COMMENT '软约束惩罚',
    is_recommended TINYINT NOT NULL DEFAULT 0 COMMENT '是否推荐',
    rank_no INT NOT NULL DEFAULT 0 COMMENT '推荐排序',
    reason_summary VARCHAR(500) NULL COMMENT '候选原因摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_candidate_semester (semester_id),
    INDEX idx_candidate_plan (plan_id),
    INDEX idx_candidate_task (teaching_task_id),
    INDEX idx_candidate_repair_task (repair_task_id),
    INDEX idx_candidate_suggestion (suggestion_id),
    INDEX idx_candidate_created (created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V5 候选位置记录';

-- 4) 优化前后对比记录
CREATE TABLE IF NOT EXISTS schedule_optimization_compare (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '优化对比ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    repair_task_id BIGINT NOT NULL COMMENT '关联修复任务ID',
    baseline_plan_id BIGINT NOT NULL COMMENT '优化前方案ID',
    optimized_plan_id BIGINT NOT NULL COMMENT '优化后方案ID',
    baseline_total_score DECIMAL(8,2) NULL COMMENT '优化前总分',
    optimized_total_score DECIMAL(8,2) NULL COMMENT '优化后总分',
    score_delta DECIMAL(8,2) NULL COMMENT '总分变化',
    baseline_risk_count INT NULL COMMENT '优化前风险数',
    optimized_risk_count INT NULL COMMENT '优化后风险数',
    risk_delta INT NULL COMMENT '风险变化',
    baseline_unscheduled_count INT NULL COMMENT '优化前未排数',
    optimized_unscheduled_count INT NULL COMMENT '优化后未排数',
    unscheduled_delta INT NULL COMMENT '未排变化',
    baseline_conflict_count INT NULL COMMENT '优化前冲突数',
    optimized_conflict_count INT NULL COMMENT '优化后冲突数',
    conflict_delta INT NULL COMMENT '冲突变化',
    compare_summary VARCHAR(1000) NULL COMMENT '对比摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_opt_compare_semester (semester_id),
    INDEX idx_opt_compare_task (repair_task_id),
    INDEX idx_opt_compare_baseline_plan (baseline_plan_id),
    INDEX idx_opt_compare_optimized_plan (optimized_plan_id),
    INDEX idx_opt_compare_created (created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V5 优化前后对比记录';

-- 5) 数据一致性检查记录
CREATE TABLE IF NOT EXISTS schedule_consistency_check (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '一致性检查ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    plan_id BIGINT NULL COMMENT '关联方案ID',
    source_plan_id BIGINT NULL COMMENT '来源方案ID',
    schedule_id BIGINT NULL COMMENT '关联正式课表ID',
    check_type VARCHAR(40) NOT NULL COMMENT '检查类型',
    check_scope VARCHAR(20) NOT NULL DEFAULT 'SEMESTER' COMMENT '检查范围：SYSTEM/SEMESTER/PLAN/SCHEDULE',
    status VARCHAR(20) NOT NULL COMMENT '检查状态：PASS/WARN/FAIL',
    issue_count INT NOT NULL DEFAULT 0 COMMENT '问题数',
    blocking_issue_count INT NOT NULL DEFAULT 0 COMMENT '阻塞问题数',
    result_summary VARCHAR(1000) NULL COMMENT '检查摘要',
    detail_json TEXT NULL COMMENT '检查详情JSON',
    checked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检查时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_consistency_semester (semester_id),
    INDEX idx_consistency_plan (plan_id),
    INDEX idx_consistency_status (status),
    INDEX idx_consistency_created (created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V5 数据一致性检查记录';

-- 6) 回归测试记录
CREATE TABLE IF NOT EXISTS schedule_regression_test (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '回归测试记录ID',
    semester_id BIGINT NULL COMMENT '所属学期ID',
    plan_id BIGINT NULL COMMENT '关联方案ID',
    source_plan_id BIGINT NULL COMMENT '来源方案ID',
    test_suite VARCHAR(64) NOT NULL COMMENT '测试套件',
    test_case VARCHAR(128) NULL COMMENT '测试用例',
    test_stage VARCHAR(32) NULL COMMENT '测试阶段，如V5_STAGE1',
    status VARCHAR(20) NOT NULL COMMENT '测试状态：PASS/FAIL/BLOCKED/RUNNING',
    duration_ms BIGINT NULL COMMENT '耗时毫秒',
    executed_by VARCHAR(64) NULL COMMENT '执行者',
    build_version VARCHAR(64) NULL COMMENT '构建版本',
    error_message VARCHAR(1000) NULL COMMENT '错误信息',
    extra_json TEXT NULL COMMENT '附加信息JSON',
    executed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_regression_semester (semester_id),
    INDEX idx_regression_plan (plan_id),
    INDEX idx_regression_status (status),
    INDEX idx_regression_stage (test_stage),
    INDEX idx_regression_created (created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V5 回归测试记录';

