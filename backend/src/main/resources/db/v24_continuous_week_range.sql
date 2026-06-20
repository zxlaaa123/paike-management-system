-- V24: continuous week range support (V10 阶段1).
--
-- 给 teaching_task、schedule、schedule_plan_item 三表加 start_week / end_week 列。
-- 默认值 1 / 20，与 V10_00 决策一致：当前默认最大周为 20。
--
-- 语义：
--   start_week/end_week 表示连续自然周闭区间。
--   week_type=ALL 区间内每周都上；ODD 取奇数周；EVEN 取偶数周。
--   历史数据回填为 1-20，与 V9 语义等价（V9 视为整学期）。
--
-- 唯一键：不依赖数据库唯一键判断区间重叠，区间重叠由服务层 WeekPatternSupport.overlap 判定。
--         V9 已有的 week_type 维度唯一键保留，本脚本不改唯一键。
--
-- 幂等：所有 DDL 用 information_schema 探测后决定是否执行，重复运行不报错。

-- ============================================================
-- 1. teaching_task 表加 start_week / end_week
-- ============================================================
SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teaching_task' AND COLUMN_NAME = 'start_week'),
        'ALTER TABLE teaching_task ADD COLUMN start_week INT NOT NULL DEFAULT 1 COMMENT ''连续周段起始周（闭区间，默认1）'' AFTER week_type',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teaching_task' AND COLUMN_NAME = 'end_week'),
        'ALTER TABLE teaching_task ADD COLUMN end_week INT NOT NULL DEFAULT 20 COMMENT ''连续周段结束周（闭区间，默认20）'' AFTER start_week',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. schedule 表加 start_week / end_week
-- ============================================================
SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'start_week'),
        'ALTER TABLE schedule ADD COLUMN start_week INT NOT NULL DEFAULT 1 COMMENT ''连续周段起始周（闭区间，默认1）'' AFTER week_type',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'end_week'),
        'ALTER TABLE schedule ADD COLUMN end_week INT NOT NULL DEFAULT 20 COMMENT ''连续周段结束周（闭区间，默认20）'' AFTER start_week',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. schedule_plan_item 表加 start_week / end_week
-- ============================================================
SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND COLUMN_NAME = 'start_week'),
        'ALTER TABLE schedule_plan_item ADD COLUMN start_week INT NOT NULL DEFAULT 1 COMMENT ''连续周段起始周（闭区间，默认1）'' AFTER week_type',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_plan_item' AND COLUMN_NAME = 'end_week'),
        'ALTER TABLE schedule_plan_item ADD COLUMN end_week INT NOT NULL DEFAULT 20 COMMENT ''连续周段结束周（闭区间，默认20）'' AFTER start_week',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
