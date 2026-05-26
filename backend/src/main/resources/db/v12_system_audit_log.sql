-- =============================================
-- V12 V6 审计日志最小闭环
-- =============================================

CREATE TABLE IF NOT EXISTS system_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审计日志ID',
    operator_id BIGINT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) NULL COMMENT '操作人名称',
    action_type VARCHAR(64) NOT NULL COMMENT '操作类型',
    target_type VARCHAR(64) NOT NULL COMMENT '操作对象类型',
    target_id BIGINT NULL COMMENT '操作对象ID',
    semester_id BIGINT NULL COMMENT '所属学期ID',
    plan_id BIGINT NULL COMMENT '排课方案ID',
    success TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功 0=失败 1=成功',
    before_summary VARCHAR(1000) NULL COMMENT '操作前摘要',
    after_summary VARCHAR(1000) NULL COMMENT '操作后摘要',
    error_code VARCHAR(64) NULL COMMENT '失败错误码',
    error_message VARCHAR(1000) NULL COMMENT '失败信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_audit_action (action_type),
    INDEX idx_audit_semester (semester_id),
    INDEX idx_audit_plan (plan_id),
    INDEX idx_audit_success (success),
    INDEX idx_audit_created (created_at)
) COMMENT='V6 系统关键操作审计日志表';
