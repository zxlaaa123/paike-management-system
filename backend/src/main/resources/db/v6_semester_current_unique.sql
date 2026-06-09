-- v6 migration: 给 semester.is_current 加唯一约束（应用层并发 setCurrent 兜底）
-- 实现方式：虚拟生成列 current_marker = IF(is_current=1, 1, NULL) + UNIQUE
-- NULL 不参与 UNIQUE 比较 → 允许多行 is_current=0，最多一行 is_current=1。
-- 使用 information_schema + PREPARE 判断，重复执行无副作用。

    -- 1) 数据 dedupe：如果历史上有 >=2 行 is_current=1，只保留 updated_at 最大的那一行；
    --    否则 ALTER 添加唯一约束会因数据冲突直接失败。
SET @ddl = (
    SELECT IF(
        (SELECT COUNT(*) FROM semester WHERE is_current = 1) > 1,
        'UPDATE semester SET is_current = 0, updated_at = NOW() WHERE is_current = 1 AND id NOT IN ( SELECT id FROM ( SELECT id FROM semester WHERE is_current = 1 ORDER BY updated_at DESC, id DESC LIMIT 1 ) AS keep )',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

    -- 2) 添加虚拟生成列 + UNIQUE 索引（幂等）
SET @ddl = (
    SELECT IF(
        NOT EXISTS ( SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'semester' AND COLUMN_NAME = 'current_marker' ),
        'ALTER TABLE semester ADD COLUMN current_marker TINYINT GENERATED ALWAYS AS (CASE WHEN is_current = 1 THEN 1 ELSE NULL END) VIRTUAL, ADD UNIQUE KEY uk_semester_current_marker (current_marker)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
