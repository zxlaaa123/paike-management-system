-- C-17: minimal role boundary. Existing users default to USER; built-in admin is ADMIN.

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sys_user'
              AND COLUMN_NAME = 'role'
        ),
        'SELECT 1',
        'ALTER TABLE sys_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT ''USER'' COMMENT ''角色：ADMIN管理员 USER普通用户'' AFTER real_name'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_user
SET role = 'ADMIN'
WHERE username = 'admin'
  AND deleted = 0;
