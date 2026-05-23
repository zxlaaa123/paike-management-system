-- =============================================
-- V3 数据库迁移脚本 - 排课方案管理
-- =============================================

-- -----------------------------------
-- 1. schedule_plan 排课方案表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS schedule_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '排课方案ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    name VARCHAR(100) NOT NULL COMMENT '方案名称',
    strategy_type VARCHAR(50) NOT NULL DEFAULT 'COMPREHENSIVE' COMMENT '方案策略类型',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '方案状态：DRAFT草稿、APPLIED已应用、ABANDONED已废弃',
    total_score DECIMAL(6,2) NULL COMMENT '方案总分',
    scheduled_count INT NOT NULL DEFAULT 0 COMMENT '已排任务数量',
    unscheduled_count INT NOT NULL DEFAULT 0 COMMENT '未排任务数量',
    conflict_count INT NOT NULL DEFAULT 0 COMMENT '冲突数量',
    description VARCHAR(500) NULL COMMENT '方案说明',
    generated_by VARCHAR(50) NULL COMMENT '生成方式或生成人',
    generated_at DATETIME NULL COMMENT '生成时间',
    applied_at DATETIME NULL COMMENT '应用时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_plan_semester (semester_id),
    INDEX idx_plan_status (status),
    INDEX idx_plan_strategy (strategy_type)
) COMMENT='排课方案表';

-- -----------------------------------
-- 2. schedule_plan_item 排课方案明细表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS schedule_plan_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '方案明细ID',
    plan_id BIGINT NOT NULL COMMENT '排课方案ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    teaching_task_id BIGINT NOT NULL COMMENT '教学任务ID',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    classroom_id BIGINT NOT NULL COMMENT '教室ID',
    weekday INT NOT NULL COMMENT '星期几：1-7',
    start_period INT NOT NULL COMMENT '开始节次',
    end_period INT NOT NULL COMMENT '结束节次',
    week_type VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT '周次类型：ALL、ODD、EVEN',
    score DECIMAL(6,2) NULL COMMENT '该安排得分',
    conflict_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否存在冲突：0否，1是',
    conflict_reason VARCHAR(500) NULL COMMENT '冲突原因',
    source_type VARCHAR(20) NOT NULL DEFAULT 'AUTO' COMMENT '来源：AUTO自动、MANUAL手动',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_plan_task_slot (plan_id, teaching_task_id, weekday, start_period, end_period),
    INDEX idx_plan_item_plan (plan_id),
    INDEX idx_plan_item_semester (semester_id),
    INDEX idx_plan_item_teacher_time (teacher_id, weekday, start_period, end_period),
    INDEX idx_plan_item_class_time (class_id, weekday, start_period, end_period),
    INDEX idx_plan_item_room_time (classroom_id, weekday, start_period, end_period)
) COMMENT='排课方案明细表';
