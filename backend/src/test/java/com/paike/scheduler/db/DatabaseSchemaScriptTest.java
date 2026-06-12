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
        String v22 = resource("db/v22_schedule_semester_unique.sql");
        String all = v6 + "\n" + v14 + "\n" + v22;

        assertTrue(all.contains("COLUMN_NAME = 'active_key'"));
        assertTrue(v6.contains("(semester_id, time_slot_id, teacher_id, active_key)"));
        assertTrue(v6.contains("(semester_id, time_slot_id, class_id, active_key)"));
        assertTrue(v6.contains("(semester_id, time_slot_id, classroom_id, active_key)"));
        assertTrue(v14.contains("(semester_id, time_slot_id, teacher_id, active_key)"));
        assertTrue(v14.contains("(semester_id, time_slot_id, class_id, active_key)"));
        assertTrue(v14.contains("(semester_id, time_slot_id, classroom_id, active_key)"));
        assertTrue(v22.contains("uk_schedule_teacher_slot"));
        assertTrue(v22.contains("(semester_id, time_slot_id, teacher_id, active_key)"));
        assertTrue(v22.contains("uk_schedule_class_slot"));
        assertTrue(v22.contains("(semester_id, time_slot_id, class_id, active_key)"));
        assertTrue(v22.contains("uk_schedule_classroom_slot"));
        assertTrue(v22.contains("(semester_id, time_slot_id, classroom_id, active_key)"));
        assertTrue(count(v14, "COLUMN_NAME = 'active_key'") >= 7,
                "v14 must keep old unique keys unless active_key exists");
        assertFalse(all.contains("ADD UNIQUE KEY uk_schedule_teacher_slot (time_slot_id, teacher_id, active_key)"));
        assertFalse(all.contains("ADD UNIQUE KEY uk_schedule_class_slot (time_slot_id, class_id, active_key)"));
        assertFalse(all.contains("ADD UNIQUE KEY uk_schedule_classroom_slot (time_slot_id, classroom_id, active_key)"));
        assertFalse(all.contains("ADD UNIQUE KEY uk_schedule_teacher_slot (time_slot_id, teacher_id, deleted)"));
        assertFalse(all.contains("ADD UNIQUE KEY uk_schedule_class_slot (time_slot_id, class_id, deleted)"));
        assertFalse(all.contains("ADD UNIQUE KEY uk_schedule_classroom_slot (time_slot_id, classroom_id, deleted)"));
    }

    @Test
    void v6BugfixConstraintsUseActiveKeyForSoftDeleteUniqueKeys() throws IOException {
        String v6 = resource("db/v6_bugfix_constraints.sql");

        assertTrue(v6.contains("active_key BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 0 ELSE NULL END) STORED"));
        assertTrue(v6.contains("uk_teacher_timeslot (teacher_id, time_slot_id, active_key)"));
        assertTrue(v6.contains("uk_plan_task_slot (plan_id, teaching_task_id, weekday, start_period, end_period)"));
        assertTrue(v6.contains("uk_locked_plan_item (plan_item_id, active_key)"));
        assertTrue(v6.contains("uk_locked_schedule (schedule_id, active_key)"));
        assertFalse(v6.contains("uk_teacher_timeslot (teacher_id, time_slot_id, deleted)"));
    }

    @Test
    void applicationRegistersV14Script() throws IOException {
        String application = resource("application.yml");

        assertTrue(application.contains("classpath:db/v14_missing_v4_v5_tables_and_schedule_keys.sql"));
    }

    @Test
    void legacyColumnMigrationsAreIdempotent() throws IOException {
        String semesterDataBind = resource("db/v3_semester_data_bind.sql");
        String scoreReport = resource("db/v2_alter_score_report.sql");

        assertTrue(semesterDataBind.contains("PREPARE stmt FROM @ddl"));
        assertTrue(semesterDataBind.contains("TABLE_NAME = 'teaching_task'"));
        assertTrue(semesterDataBind.contains("COLUMN_NAME = 'semester_id'"));
        assertTrue(semesterDataBind.contains("TABLE_NAME = 'schedule'"));
        assertTrue(semesterDataBind.contains("COLUMN_NAME = 'plan_id'"));
        assertTrue(semesterDataBind.contains("EXECUTE stmt"));

        assertTrue(scoreReport.contains("PREPARE stmt FROM @ddl"));
        assertTrue(scoreReport.contains("TABLE_NAME = 'schedule_score_report'"));
        assertTrue(scoreReport.contains("COLUMN_NAME = 'grade_name'"));
        assertTrue(scoreReport.contains("EXECUTE stmt"));
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
    void sqlInitializerFailsFastInsteadOfSwallowingMigrationErrors() throws IOException {
        String application = resource("application.yml");

        assertTrue(application.contains("continue-on-error: false"));
        assertFalse(application.contains("continue-on-error: true"));
    }

    @Test
    void v5ColumnMigrationsAreIdempotent() throws IOException {
        String v5Stage1 = resource("db/v5_stage1.sql");
        String v5Stage3 = resource("db/v5_stage3.sql");
        String v5Stage6 = resource("db/v5_stage6.sql");

        assertTrue(v5Stage1.contains("PREPARE stmt FROM @ddl"));
        assertTrue(v5Stage1.contains("COLUMN_NAME = 'plan_mode'"));
        assertTrue(v5Stage1.contains("INDEX_NAME = 'idx_schedule_plan_mode'"));

        assertTrue(v5Stage3.contains("PREPARE stmt FROM @ddl"));
        assertTrue(v5Stage3.contains("COLUMN_NAME = 'cancel_reason'"));
        assertTrue(v5Stage3.contains("INDEX_NAME = 'idx_repair_task_result_plan'"));

        assertTrue(v5Stage6.contains("PREPARE stmt FROM @ddl"));
        assertTrue(v5Stage6.contains("COLUMN_NAME = 'source_schedule_id'"));
        assertTrue(v5Stage6.contains("INDEX_NAME = 'idx_plan_repair_task'"));
    }

    @Test
    void scheduleSemesterUniqueMigrationIsRegistered() throws IOException {
        String migration = resource("db/v22_schedule_semester_unique.sql");
        String application = resource("application.yml");

        assertTrue(migration.contains("DROP INDEX uk_schedule_teacher_slot"));
        assertTrue(migration.contains("DROP INDEX uk_schedule_class_slot"));
        assertTrue(migration.contains("DROP INDEX uk_schedule_classroom_slot"));
        assertTrue(migration.contains("(semester_id, time_slot_id, teacher_id, active_key)"));
        assertTrue(migration.contains("(semester_id, time_slot_id, class_id, active_key)"));
        assertTrue(migration.contains("(semester_id, time_slot_id, classroom_id, active_key)"));
        assertTrue(application.contains("classpath:db/v22_schedule_semester_unique.sql"));
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

    @Test
    void scheduleReportSemesterDeletedMigrationIsRegistered() throws IOException {
        String v14 = resource("db/v14_missing_v4_v5_tables_and_schedule_keys.sql");
        String migration = resource("db/v18_schedule_report_semester_deleted.sql");
        String application = resource("application.yml");

        assertTrue(v14.contains("CREATE TABLE IF NOT EXISTS schedule_report"));
        assertTrue(v14.contains("semester_id BIGINT NULL COMMENT '所属学期ID'"));
        assertTrue(v14.contains("deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除'"));
        assertTrue(migration.contains("TABLE_NAME = 'schedule_report'"));
        assertTrue(migration.contains("ADD COLUMN semester_id BIGINT NULL COMMENT ''所属学期ID''"));
        assertTrue(migration.contains("ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除''"));
        assertTrue(migration.contains("idx_schedule_report_plan_deleted"));
        assertTrue(migration.contains("idx_schedule_report_semester_deleted_created"));
        assertTrue(migration.contains("JOIN schedule_plan p ON p.id = r.plan_id"));
        assertTrue(application.contains("classpath:db/v18_schedule_report_semester_deleted.sql"));
    }

    @Test
    void scoreDetailDeletedMigrationIsRegistered() throws IOException {
        String schema = resource("db/v3_score.sql");
        String migration = resource("db/v19_score_detail_deleted.sql");
        String application = resource("application.yml");

        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS schedule_score_detail"));
        assertTrue(schema.contains("deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除'"));
        assertTrue(schema.contains("idx_score_detail_plan_deleted"));
        assertTrue(schema.contains("idx_score_detail_semester_deleted"));
        assertTrue(migration.contains("TABLE_NAME = 'schedule_score_detail'"));
        assertTrue(migration.contains("ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除，1已删除''"));
        assertTrue(migration.contains("idx_score_detail_plan_deleted"));
        assertTrue(migration.contains("idx_score_detail_semester_deleted"));
        assertTrue(application.contains("classpath:db/v19_score_detail_deleted.sql"));
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
