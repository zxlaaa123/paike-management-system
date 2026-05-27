package com.paike.scheduler.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaScriptTest {

    private static final List<String> MISSING_TABLES = List.of(
            "schedule_generate_log",
            "schedule_unassigned_task",
            "schedule_adjust_log",
            "schedule_locked_item",
            "schedule_report"
    );

    @Test
    void v14DefinesMissingV4V5Tables() throws IOException {
        String sql = resource("db/v14_missing_v4_v5_tables_and_schedule_keys.sql");

        for (String table : MISSING_TABLES) {
            assertTrue(
                    sql.contains("CREATE TABLE IF NOT EXISTS " + table),
                    "Missing CREATE TABLE for " + table);
        }
    }

    @Test
    void tableLogicTablesIncludeDeletedColumn() throws IOException {
        String sql = resource("db/v14_missing_v4_v5_tables_and_schedule_keys.sql");

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS schedule_unassigned_task"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS schedule_adjust_log"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS schedule_locked_item"));
        assertTrue(sql.contains("deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除'"));
    }

    @Test
    void scheduleUniqueKeysUseActiveKeyInsteadOfDeleted() throws IOException {
        String v6 = resource("db/v6_schedule_index.sql");
        String v14 = resource("db/v14_missing_v4_v5_tables_and_schedule_keys.sql");
        String all = v6 + "\n" + v14;

        assertTrue(all.contains("COLUMN_NAME = 'active_key'"));
        assertTrue(all.contains("uk_schedule_teacher_slot (time_slot_id, teacher_id, active_key)"));
        assertTrue(all.contains("uk_schedule_class_slot (time_slot_id, class_id, active_key)"));
        assertTrue(all.contains("uk_schedule_classroom_slot (time_slot_id, classroom_id, active_key)"));
        assertFalse(all.contains("ADD UNIQUE KEY uk_schedule_teacher_slot (time_slot_id, teacher_id, deleted)"));
        assertFalse(all.contains("ADD UNIQUE KEY uk_schedule_class_slot (time_slot_id, class_id, deleted)"));
        assertFalse(all.contains("ADD UNIQUE KEY uk_schedule_classroom_slot (time_slot_id, classroom_id, deleted)"));
    }

    @Test
    void applicationRegistersV14Script() throws IOException {
        String application = resource("application.yml");

        assertTrue(application.contains("classpath:db/v14_missing_v4_v5_tables_and_schedule_keys.sql"));
    }

    @Test
    void sysUserRoleMigrationIsRegistered() throws IOException {
        String schema = resource("db/schema.sql");
        String migration = resource("db/v15_sys_user_role.sql");
        String application = resource("application.yml");

        assertTrue(schema.contains("role VARCHAR(20) NOT NULL DEFAULT 'USER'"));
        assertTrue(migration.contains("ALTER TABLE sys_user ADD COLUMN role"));
        assertTrue(migration.contains("SET role = 'ADMIN'"));
        assertTrue(application.contains("classpath:db/v15_sys_user_role.sql"));
    }

    private String resource(String path) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(inputStream, "Missing resource " + path);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
