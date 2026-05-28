-- Flyway V1 baseline migration (consolidated from spring.sql.init scripts)
-- All statements use IF NOT EXISTS / IGNORE for safe repeatable execution

-- === From: schema.sql ===
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码（加密）',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN管理员 USER普通用户',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sys_user_username (username)
) COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    teacher_no VARCHAR(50) NOT NULL COMMENT '教师编号',
    name VARCHAR(50) NOT NULL COMMENT '教师姓名',
    department VARCHAR(100) DEFAULT NULL COMMENT '所属学院/部门',
    phone VARCHAR(30) DEFAULT NULL COMMENT '联系电话',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_teacher_no (teacher_no)
) COMMENT='教师表';

CREATE TABLE IF NOT EXISTS class_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    class_name VARCHAR(100) NOT NULL COMMENT '班级名称',
    major VARCHAR(100) DEFAULT NULL COMMENT '专业名称',
    grade VARCHAR(20) DEFAULT NULL COMMENT '年级',
    student_count INT NOT NULL DEFAULT 0 COMMENT '班级人数',
    head_teacher VARCHAR(50) DEFAULT NULL COMMENT '班主任',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_class_name (class_name)
) COMMENT='班级表';

CREATE TABLE IF NOT EXISTS classroom (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    room_name VARCHAR(100) NOT NULL COMMENT '教室名称',
    building VARCHAR(100) DEFAULT NULL COMMENT '教学楼',
    capacity INT NOT NULL DEFAULT 0 COMMENT '教室容量',
    room_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL' COMMENT '教室类型：NORMAL普通教室 MULTIMEDIA多媒体教室 LAB实验室 COMPUTER机房',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_room_name (room_name)
) COMMENT='教室表';

CREATE TABLE IF NOT EXISTS course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    course_no VARCHAR(50) NOT NULL COMMENT '课程编号',
    course_name VARCHAR(100) NOT NULL COMMENT '课程名称',
    course_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL' COMMENT '课程类型：NORMAL普通课 EXPERIMENT实验课 COMPUTER机房课 PE体育课',
    course_nature VARCHAR(50) DEFAULT NULL COMMENT '课程性质',
    total_hours INT NOT NULL DEFAULT 0 COMMENT '总学时',
    weekly_hours INT NOT NULL DEFAULT 0 COMMENT '每周课时',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_course_no (course_no)
) COMMENT='课程表';

CREATE TABLE IF NOT EXISTS teaching_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    weekly_hours INT NOT NULL DEFAULT 0 COMMENT '每周课时',
    need_continuous TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要连续排课：0否 1是',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='教学任务表';

CREATE TABLE IF NOT EXISTS time_slot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    day_of_week TINYINT NOT NULL COMMENT '星期几：1周一 2周二 3周三 4周四 5周五',
    period_no TINYINT NOT NULL COMMENT '大节序号：1第1-2节 2第3-4节 3第5-6节 4第7-8节',
    time_label VARCHAR(50) NOT NULL COMMENT '时间段标签，如：周一 第1-2节',
    sort_order TINYINT NOT NULL COMMENT '排序序号：1~20',
    UNIQUE KEY uk_day_period (day_of_week, period_no)
) COMMENT='时间段表';

INSERT IGNORE INTO time_slot (day_of_week, period_no, time_label, sort_order) VALUES
(1, 1, '周一 第1-2节', 1),
(1, 2, '周一 第3-4节', 2),
(1, 3, '周一 第5-6节', 3),
(1, 4, '周一 第7-8节', 4),
(2, 1, '周二 第1-2节', 5),
(2, 2, '周二 第3-4节', 6),
(2, 3, '周二 第5-6节', 7),
(2, 4, '周二 第7-8节', 8),
(3, 1, '周三 第1-2节', 9),
(3, 2, '周三 第3-4节', 10),
(3, 3, '周三 第5-6节', 11),
(3, 4, '周三 第7-8节', 12),
(4, 1, '周四 第1-2节', 13),
(4, 2, '周四 第3-4节', 14),
(4, 3, '周四 第5-6节', 15),
(4, 4, '周四 第7-8节', 16),
(5, 1, '周五 第1-2节', 17),
(5, 2, '周五 第3-4节', 18),
(5, 3, '周五 第5-6节', 19),
(5, 4, '周五 第7-8节', 20);

-- V1 schedule table（使用 IF NOT EXISTS 避免重建）
CREATE TABLE IF NOT EXISTS schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    teaching_task_id BIGINT NOT NULL COMMENT '教学任务ID',
    course_id BIGINT NOT NULL COMMENT '课程ID（冗余）',
    teacher_id BIGINT NOT NULL COMMENT '教师ID（冗余）',
    class_id BIGINT NOT NULL COMMENT '班级ID（冗余）',
    time_slot_id BIGINT NOT NULL COMMENT '时间段ID',
    classroom_id BIGINT NOT NULL COMMENT '教室ID',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='排课表';

-- === From: v2_schema.sql ===
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
    semester_id BIGINT DEFAULT NULL COMMENT '所属学期ID',
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
    semester_id BIGINT DEFAULT NULL COMMENT '所属学期ID',
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

-- === From: v2_alter_schedule.sql ===
-- =============================================
-- V2 schedule 表扩展字段（幂等版本）
-- 添加排课来源和批次关联字段
-- 使用存储过程实现幂等执行，重复执行不会报错
-- =============================================

DROP PROCEDURE IF EXISTS add_schedule_columns_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_schedule_columns_if_not_exists()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule'
                     AND COLUMN_NAME = 'source_type') THEN
        ALTER TABLE schedule
            ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL' COMMENT '排课来源：MANUAL手动 AUTO自动';
    END IF;

    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule'
                     AND COLUMN_NAME = 'batch_id') THEN
        ALTER TABLE schedule
            ADD COLUMN batch_id BIGINT DEFAULT NULL COMMENT '自动排课批次ID，手动排课为空';
    END IF;
END //
DELIMITER ;

CALL add_schedule_columns_if_not_exists();
DROP PROCEDURE IF EXISTS add_schedule_columns_if_not_exists;

-- === From: v2_alter_score_report.sql ===
-- 为 schedule_score_report 表添加 grade_name 字段（幂等）

DROP PROCEDURE IF EXISTS add_grade_name_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_grade_name_if_not_exists()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'schedule_score_report'
                     AND COLUMN_NAME = 'grade_name') THEN
        ALTER TABLE schedule_score_report ADD COLUMN grade_name VARCHAR(20) DEFAULT NULL COMMENT '等级名称：优秀/良好/一般/较差/需要调整' AFTER grade;
    END IF;
END //
DELIMITER ;

CALL add_grade_name_if_not_exists();
DROP PROCEDURE IF EXISTS add_grade_name_if_not_exists;

-- === From: v3_semester.sql ===
-- =============================================
-- V3 数据库迁移脚本 - 学期管理
-- 在 V1/V2 基础上增量扩展，不删除或重建已有表
-- =============================================

-- -----------------------------------
-- 1. semester 学期表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS semester (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学期ID',
    name VARCHAR(100) NOT NULL COMMENT '学期名称，例如：2025-2026学年第一学期',
    school_year VARCHAR(30) NOT NULL COMMENT '学年，例如：2025-2026',
    term VARCHAR(30) NOT NULL COMMENT '学期，例如：第一学期、第二学期',
    start_date DATE NULL COMMENT '学期开始日期',
    end_date DATE NULL COMMENT '学期结束日期',
    is_current TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前学期：0否，1是',
    status VARCHAR(20) NOT NULL DEFAULT '未开始' COMMENT '状态：未开始、进行中、已结束',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='学期表';

-- -----------------------------------
-- 2. 初始化默认学期（如果没有任何学期数据）
-- -----------------------------------
INSERT INTO semester (name, school_year, term, start_date, end_date, is_current, status, remark)
SELECT '2025-2026学年第二学期', '2025-2026', '第二学期', '2026-02-23', '2026-07-10', 1, '进行中', 'V3默认学期'
WHERE NOT EXISTS (SELECT 1 FROM semester LIMIT 1);

-- === From: v3_schedule_plan.sql ===
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_plan_task_slot (plan_id, teaching_task_id, weekday, start_period, end_period),
    INDEX idx_plan_item_plan (plan_id),
    INDEX idx_plan_item_semester (semester_id),
    INDEX idx_plan_item_teacher_time (teacher_id, weekday, start_period, end_period),
    INDEX idx_plan_item_class_time (class_id, weekday, start_period, end_period),
    INDEX idx_plan_item_room_time (classroom_id, weekday, start_period, end_period)
) COMMENT='排课方案明细表';

-- === From: v3_score.sql ===
-- =============================================
-- V3 数据库迁移脚本 - 评分明细与规则权重
-- =============================================

-- -----------------------------------
-- 1. schedule_rule_weight 排课规则权重表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS schedule_rule_weight (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '规则权重ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    strategy_type VARCHAR(50) NOT NULL COMMENT '方案策略类型',
    rule_code VARCHAR(100) NOT NULL COMMENT '规则编码',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(20) NOT NULL COMMENT '规则类型：HARD硬约束、SOFT软约束',
    weight DECIMAL(6,2) NOT NULL DEFAULT 1.00 COMMENT '规则权重',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否，1是',
    description VARCHAR(500) NULL COMMENT '规则说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_rule_weight_semester_strategy_rule (semester_id, strategy_type, rule_code),
    INDEX idx_rule_weight_semester (semester_id),
    INDEX idx_rule_weight_strategy (strategy_type),
    INDEX idx_rule_weight_rule_code (rule_code)
) COMMENT='排课规则权重表';

-- -----------------------------------
-- 2. schedule_score_detail 排课评分明细表
-- -----------------------------------
CREATE TABLE IF NOT EXISTS schedule_score_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评分明细ID',
    plan_id BIGINT NOT NULL COMMENT '排课方案ID',
    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
    rule_code VARCHAR(100) NOT NULL COMMENT '规则编码',
    rule_type VARCHAR(20) NOT NULL DEFAULT 'SOFT' COMMENT '规则类型：HARD硬约束、SOFT软约束',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    score DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '该项得分或扣分',
    max_score DECIMAL(6,2) NULL COMMENT '该项最高分',
    violation_count INT NOT NULL DEFAULT 0 COMMENT '违规次数',
    detail_message VARCHAR(1000) NULL COMMENT '评分说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    INDEX idx_score_detail_plan (plan_id),
    INDEX idx_score_detail_plan_deleted (plan_id, deleted),
    INDEX idx_score_detail_semester (semester_id),
    INDEX idx_score_detail_semester_deleted (semester_id, deleted),
    INDEX idx_score_detail_rule (rule_code)
) COMMENT='排课评分明细表';

-- -----------------------------------
-- 3. 初始化默认规则权重（综合最优策略）
-- -----------------------------------
INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'TEACHER_TIME_CONFLICT', '教师时间冲突', 'HARD', 100, 1, '同一教师同一时间不能上两门课'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'CLASS_TIME_CONFLICT', '班级时间冲突', 'HARD', 100, 1, '同一班级同一时间不能上两门课'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'CLASSROOM_TIME_CONFLICT', '教室时间冲突', 'HARD', 100, 1, '同一教室同一时间不能安排两门课'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'TEACHER_UNAVAILABLE', '教师禁排时间', 'HARD', 90, 1, '教师禁排时间不能安排课程'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'CLASSROOM_CAPACITY', '教室容量不足', 'HARD', 80, 1, '教室容量必须满足班级人数'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'CLASSROOM_TYPE_MISMATCH', '教室类型不匹配', 'HARD', 80, 1, '课程类型应匹配教室类型'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'CLASS_DAILY_BALANCE', '班级每日均衡', 'SOFT', 30, 1, '班级每天课程数量尽量均衡'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'TEACHER_DAILY_LOAD', '教师每日负载', 'SOFT', 30, 1, '教师每天上课数量尽量合理'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'CONTINUOUS_PERIOD_LIMIT', '连续上课限制', 'SOFT', 25, 1, '连续上课节次不宜过长'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'COURSE_DISTRIBUTION', '课程分布均衡', 'SOFT', 25, 1, '同一课程不要过度集中'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'COMPREHENSIVE', 'CLASSROOM_UTILIZATION', '教室利用率', 'SOFT', 20, 1, '尽量提高教室使用率'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

-- 教师优先策略
INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'TEACHER_PRIORITY', 'TEACHER_UNAVAILABLE', '教师禁排时间', 'HARD', 100, 1, '教师禁排时间不能安排课程'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'TEACHER_PRIORITY', 'TEACHER_TIME_CONFLICT', '教师时间冲突', 'HARD', 100, 1, '同一教师同一时间不能上两门课'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'TEACHER_PRIORITY', 'TEACHER_DAILY_LOAD', '教师每日负载', 'SOFT', 50, 1, '教师每天上课数量尽量合理'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'TEACHER_PRIORITY', 'CONTINUOUS_PERIOD_LIMIT', '连续上课限制', 'SOFT', 45, 1, '连续上课节次不宜过长'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'TEACHER_PRIORITY', 'CLASS_DAILY_BALANCE', '班级每日均衡', 'SOFT', 20, 1, '班级每天课程数量尽量均衡'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'TEACHER_PRIORITY', 'CLASSROOM_UTILIZATION', '教室利用率', 'SOFT', 10, 1, '尽量提高教室使用率'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

-- 班级均衡策略
INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASS_BALANCE', 'CLASS_TIME_CONFLICT', '班级时间冲突', 'HARD', 100, 1, '同一班级同一时间不能上两门课'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASS_BALANCE', 'CLASS_DAILY_BALANCE', '班级每日均衡', 'SOFT', 50, 1, '班级每天课程数量尽量均衡'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASS_BALANCE', 'COURSE_DISTRIBUTION', '课程分布均衡', 'SOFT', 45, 1, '同一课程不要过度集中'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASS_BALANCE', 'CONTINUOUS_PERIOD_LIMIT', '连续上课限制', 'SOFT', 40, 1, '连续上课节次不宜过长'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASS_BALANCE', 'MORNING_THEORY_PRIORITY', '理论课优先上午', 'SOFT', 25, 1, '理论课尽量安排在上午'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASS_BALANCE', 'TEACHER_DAILY_LOAD', '教师每日负载', 'SOFT', 20, 1, '教师每天上课数量尽量合理'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASS_BALANCE', 'CLASSROOM_UTILIZATION', '教室利用率', 'SOFT', 10, 1, '尽量提高教室使用率'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

-- 教室利用率策略
INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASSROOM_UTILIZATION', 'CLASSROOM_TIME_CONFLICT', '教室时间冲突', 'HARD', 100, 1, '同一教室同一时间不能安排两门课'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASSROOM_UTILIZATION', 'CLASSROOM_CAPACITY', '教室容量不足', 'HARD', 90, 1, '教室容量必须满足班级人数'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASSROOM_UTILIZATION', 'CLASSROOM_TYPE_MISMATCH', '教室类型不匹配', 'HARD', 90, 1, '课程类型应匹配教室类型'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASSROOM_UTILIZATION', 'CLASSROOM_UTILIZATION', '教室利用率', 'SOFT', 60, 1, '尽量提高教室使用率'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASSROOM_UTILIZATION', 'CLASS_DAILY_BALANCE', '班级每日均衡', 'SOFT', 20, 1, '班级每天课程数量尽量均衡'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

INSERT INTO schedule_rule_weight (semester_id, strategy_type, rule_code, rule_name, rule_type, weight, enabled, description)
SELECT s.id, 'CLASSROOM_UTILIZATION', 'TEACHER_DAILY_LOAD', '教师每日负载', 'SOFT', 20, 1, '教师每天上课数量尽量合理'
FROM semester s WHERE s.is_current = 1
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

-- === From: v3_semester_data_bind.sql ===
-- =============================================
-- V3 数据库迁移脚本 - 核心数据绑定学期
-- 为 teaching_task 和 schedule 表增加 semester_id
-- =============================================

-- -----------------------------------
-- 1. teaching_task 增加 semester_id
-- -----------------------------------
ALTER TABLE teaching_task ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id;

-- -----------------------------------
-- 2. schedule 增加 semester_id 和 plan_id
-- -----------------------------------
ALTER TABLE schedule ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id;
ALTER TABLE schedule ADD COLUMN plan_id BIGINT NULL COMMENT '来源排课方案ID' AFTER batch_id;

-- -----------------------------------
-- 3. 旧数据迁移到当前学期（仅当存在当前学期时执行）
-- -----------------------------------
UPDATE teaching_task tt
SET tt.semester_id = (SELECT id FROM semester WHERE is_current = 1 LIMIT 1)
WHERE tt.semester_id IS NULL
  AND EXISTS (SELECT 1 FROM semester WHERE is_current = 1 LIMIT 1);

UPDATE schedule s
SET s.semester_id = (SELECT id FROM semester WHERE is_current = 1 LIMIT 1)
WHERE s.semester_id IS NULL
  AND EXISTS (SELECT 1 FROM semester WHERE is_current = 1 LIMIT 1);
