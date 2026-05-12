-- =============================================
-- V2 schedule 表扩展字段
-- 添加排课来源和批次关联字段
-- 注意：此脚本应在 v2_schema.sql 之后执行
-- 由于 Spring Boot sql init 不支持存储过程，
-- 新环境首次启动时会因列不存在而执行成功，
-- 重复执行会报错，因此仅在首次部署时由 Spring 自动执行
-- 开发环境已通过手动执行迁移完成
-- =============================================

ALTER TABLE schedule
    ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL' COMMENT '排课来源：MANUAL手动 AUTO自动';

ALTER TABLE schedule
    ADD COLUMN batch_id BIGINT DEFAULT NULL COMMENT '自动排课批次ID，手动排课为空';
