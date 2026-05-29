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
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排课规则权重表';

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
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排课评分明细表';

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
