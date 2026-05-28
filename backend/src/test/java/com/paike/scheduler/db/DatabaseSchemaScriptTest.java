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
        assertTrue(count(v14, "COLUMN_NAME = 'active_key'") >= 7,
                "v14 must keep old unique keys unless active_key exists");
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

    @Test
    void scheduleSearchOrderIndexMigrationIsRegistered() throws IOException {
        String migration = resource("db/v16_schedule_search_order_index.sql");
        String application = resource("application.yml");

        assertTrue(migration.contains("idx_schedule_semester_deleted_created"));
        assertTrue(migration.contains("(semester_id, deleted, create_time, id)"));
        assertTrue(application.contains("classpath:db/v16_schedule_search_order_index.sql"));
    }

    @Test
    void reportBatchSemesterMigrationIsRegistered() throws IOException {
        String schema = resource("db/v2_schema.sql");
        String migration = resource("db/v17_report_batch_semester.sql");
        String application = resource("application.yml");

        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS schedule_conflict_report"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS auto_schedule_batch"));
        assertTrue(schema.contains("semester_id BIGINT DEFAULT NULL COMMENT '所属学期ID'"));
        assertTrue(migration.contains("TABLE_NAME = 'schedule_conflict_report'"));
        assertTrue(migration.contains("TABLE_NAME = 'auto_schedule_batch'"));
        assertTrue(migration.contains("idx_conflict_report_semester"));
        assertTrue(migration.contains("idx_auto_schedule_batch_semester"));
        assertTrue(application.contains("classpath:db/v17_report_batch_semester.sql"));
    }

    private String resource(String path) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(inputStream, "Missing resource " + path);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int count(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
