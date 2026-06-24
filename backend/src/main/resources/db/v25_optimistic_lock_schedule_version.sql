-- V25: optimistic lock version column for schedule table (P1 #4 乐观锁，首期仅 schedule 一张表).
--
-- 背景：
--   课表拖拽调整（V4ScheduleAdjustmentService）与已应用方案同步（SchedulePlanService.syncAppliedSchedule）
--   都对同一条 schedule 记录做 updateById。两个用户同时编辑时存在「丢失更新」风险，
--   此前仅靠 V4 内实例级 synchronized 兜底，粒度过粗且多实例部署失效。
--
-- 方案：
--   给 schedule 表加 version 列，配合实体 @Version + MyBatis-Plus OptimisticLockerInnerInterceptor，
--   updateById 自动带上 WHERE version=? 并自增 version；返回 0 行即说明被他人改过，抛 CONCURRENT_MODIFIED。
--
-- 范围：本期仅 schedule。schedule_plan_item / schedule_plan 是否扩展待验证后再定。
--
-- 幂等：用 information_schema 探测列是否存在后再决定是否执行，重复运行不报错。
-- 历史数据：version 默认 0，与新插入记录一致。

SET @ddl = (
    SELECT IF(
        NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'version'),
        'ALTER TABLE schedule ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号（V25 并发编辑保护）'' AFTER plan_id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
