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

-- 索引（先删后建，确保幂等）
DROP INDEX IF EXISTS idx_semester_current ON semester;
CREATE INDEX idx_semester_current ON semester(is_current);
DROP INDEX IF EXISTS idx_semester_school_year ON semester;
CREATE INDEX idx_semester_school_year ON semester(school_year);

-- -----------------------------------
-- 2. 初始化默认学期（如果没有任何学期数据）
-- -----------------------------------
INSERT INTO semester (name, school_year, term, start_date, end_date, is_current, status, remark)
SELECT '2025-2026学年第二学期', '2025-2026', '第二学期', '2026-02-23', '2026-07-10', 1, '进行中', 'V3默认学期'
WHERE NOT EXISTS (SELECT 1 FROM semester LIMIT 1);
