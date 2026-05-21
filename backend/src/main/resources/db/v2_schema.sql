-- =============================================
-- V2 数据库迁移脚本
-- 在 V1 基础上增量扩展，不删除或重建 V1 已有表
-- =============================================

-- -----------------------------------
-- 1. teacher_unavailable_time 教师禁排时间表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS teacher_unavailable_time (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    time_slot_id BIGINT NOT NULL COMMENT '时间段ID',
    reason VARCHAR(255) DEFAULT NULL COMMENT '禁排原因',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    active_key BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 0 ELSE id END) STORED,
    UNIQUE KEY uk_teacher_timeslot (teacher_id, time_slot_id, active_key)
) COMMENT='教师禁排时间表';

-- -----------------------------------
-- 2. schedule_rule_config 排课规则配置表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS schedule_rule_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    rule_key VARCHAR(100) NOT NULL COMMENT '规则键',
    rule_value VARCHAR(100) NOT NULL COMMENT '规则值',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '规则说明',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0停用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_rule_key (rule_key)
) COMMENT='排课规则配置表';

-- -----------------------------------
-- 3. auto_schedule_batch 自动排课批次表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS auto_schedule_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    batch_no VARCHAR(50) NOT NULL COMMENT '批次号',
    total_task_count INT NOT NULL DEFAULT 0 COMMENT '参与排课任务数',
    success_task_count INT NOT NULL DEFAULT 0 COMMENT '成功排课任务数',
    failed_task_count INT NOT NULL DEFAULT 0 COMMENT '未排任务数',
    generated_schedule_count INT NOT NULL DEFAULT 0 COMMENT '生成排课记录数',
    clear_old_schedule TINYINT NOT NULL DEFAULT 0 COMMENT '是否清空旧排课：1是 0否',
    status VARCHAR(30) NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING执行中 SUCCESS完成 PARTIAL部分成功 FAILED失败',
    message VARCHAR(500) DEFAULT NULL COMMENT '执行结果说明',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_batch_no (batch_no)
) COMMENT='自动排课批次表';

-- -----------------------------------
-- 4. unscheduled_task 未排任务表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS unscheduled_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    batch_id BIGINT NOT NULL COMMENT '自动排课批次ID',
    task_id BIGINT NOT NULL COMMENT '教学任务ID',
    course_id BIGINT DEFAULT NULL COMMENT '课程ID',
    teacher_id BIGINT DEFAULT NULL COMMENT '教师ID',
    class_id BIGINT DEFAULT NULL COMMENT '班级ID',
    required_slots INT NOT NULL DEFAULT 0 COMMENT '需要排的大节数',
    scheduled_slots INT NOT NULL DEFAULT 0 COMMENT '已排大节数',
    remaining_slots INT NOT NULL DEFAULT 0 COMMENT '剩余未排大节数',
    reason_type VARCHAR(50) DEFAULT NULL COMMENT '未排原因类型',
    reason_message VARCHAR(500) DEFAULT NULL COMMENT '未排原因说明',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='未排任务表';

-- -----------------------------------
-- 5. schedule_conflict_report 排课冲突报告表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS schedule_conflict_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    report_no VARCHAR(50) NOT NULL COMMENT '报告编号',
    conflict_type VARCHAR(50) NOT NULL COMMENT '冲突类型',
    object_type VARCHAR(50) DEFAULT NULL COMMENT '冲突对象类型：TEACHER教师 CLASS班级 CLASSROOM教室 TASK任务 SCHEDULE排课',
    object_id BIGINT DEFAULT NULL COMMENT '冲突对象ID',
    object_name VARCHAR(100) DEFAULT NULL COMMENT '冲突对象名称',
    time_slot_id BIGINT DEFAULT NULL COMMENT '时间段ID',
    related_schedule_ids VARCHAR(255) DEFAULT NULL COMMENT '相关排课记录ID',
    description VARCHAR(500) DEFAULT NULL COMMENT '冲突说明',
    suggestion VARCHAR(500) DEFAULT NULL COMMENT '处理建议',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_report_no (report_no)
) COMMENT='排课冲突报告表';

-- -----------------------------------
-- 6. schedule_score_report 课表评分报告表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS schedule_score_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    score INT NOT NULL DEFAULT 100 COMMENT '总分，0-100',
    grade VARCHAR(20) DEFAULT NULL COMMENT '等级：EXCELLENT优秀 GOOD良好 NORMAL一般 POOR较差 BAD需要调整',
    conflict_count INT NOT NULL DEFAULT 0 COMMENT '硬冲突数量',
    unfinished_task_count INT NOT NULL DEFAULT 0 COMMENT '未排满任务数量',
    teacher_overload_count INT NOT NULL DEFAULT 0 COMMENT '教师超负荷数量',
    class_overload_count INT NOT NULL DEFAULT 0 COMMENT '班级超负荷数量',
    friday_afternoon_count INT NOT NULL DEFAULT 0 COMMENT '周五下午课程数量',
    deduction_detail TEXT DEFAULT NULL COMMENT '扣分详情',
    suggestion TEXT DEFAULT NULL COMMENT '优化建议',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='课表评分报告表';

-- -----------------------------------
-- 7. 初始化默认排课规则
-- -----------------------------------
INSERT INTO schedule_rule_config (rule_key, rule_value, rule_name, description, enabled) VALUES
('TEACHER_MAX_DAILY_SLOTS', '3', '教师每天最多课程大节数', '每位教师每天最多安排的大节数量', 1),
('CLASS_MAX_DAILY_SLOTS', '4', '班级每天最多课程大节数', '每个班级每天最多安排的大节数量', 1),
('PRIORITIZE_MORNING', 'true', '优先上午排课', '自动排课时优先安排上午时间段', 1),
('AVOID_FRIDAY_AFTERNOON', 'true', '避免周五下午排课', '自动排课时尽量避免安排周五下午课程', 1),
('ALLOW_SAME_COURSE_SAME_DAY', 'false', '允许同一课程同一天重复出现', '同一班级同一课程是否可以在一天内排多次', 1)
ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value);
