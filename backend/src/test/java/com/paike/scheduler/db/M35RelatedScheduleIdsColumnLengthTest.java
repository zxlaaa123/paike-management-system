package com.paike.scheduler.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M35RelatedScheduleIdsColumnLengthTest {

    @Test
    void conflictReportSchemaUsesTextForRelatedScheduleIds() throws IOException {
        String v2Schema = resource("db/v2_schema.sql");
        String baseline = resource("db/migration/V1__baseline.sql");

        assertTrue(v2Schema.contains("related_schedule_ids TEXT NULL COMMENT '相关排课记录ID'"));
        assertTrue(baseline.contains("related_schedule_ids TEXT NULL COMMENT '相关排课记录ID'"));
        assertFalse(v2Schema.contains("related_schedule_ids VARCHAR(255)"));
        assertFalse(baseline.contains("related_schedule_ids VARCHAR(255)"));
    }

    @Test
    void existingConflictReportColumnIsWidenedByRegisteredMigration() throws IOException {
        String migration = resource("db/v20_schedule_conflict_related_ids_text.sql");
        String application = resource("application.yml");

        assertTrue(migration.contains("TABLE_NAME = 'schedule_conflict_report'"));
        assertTrue(migration.contains("COLUMN_NAME = 'related_schedule_ids'"));
        assertTrue(migration.contains("DATA_TYPE <> 'text'"));
        assertTrue(migration.contains("MODIFY COLUMN related_schedule_ids TEXT NULL COMMENT ''相关排课记录ID''"));
        assertTrue(application.contains("classpath:db/v20_schedule_conflict_related_ids_text.sql"));
    }

    private String resource(String path) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(inputStream, "Missing resource " + path);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

