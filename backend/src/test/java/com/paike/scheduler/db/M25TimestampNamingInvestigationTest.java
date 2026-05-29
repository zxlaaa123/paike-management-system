package com.paike.scheduler.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M25TimestampNamingInvestigationTest {

    private static final Path DB_DIR = Path.of("src", "main", "resources", "db");

    @Test
    void autoScheduleBatchBaselineContainsMappedUpdateTimeColumn() throws IOException {
        String v2Schema = Files.readString(Path.of("src", "main", "resources", "db", "v2_schema.sql"),
                StandardCharsets.UTF_8);
        String v1Baseline = Files.readString(Path.of("src", "main", "resources", "db", "migration", "V1__baseline.sql"),
                StandardCharsets.UTF_8);
        String v9Migration = Files.readString(
                Path.of("src", "main", "resources", "db", "v9_auto_schedule_batch_update_time.sql"),
                StandardCharsets.UTF_8);
        String entity = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "entity", "AutoScheduleBatch.java"),
                StandardCharsets.UTF_8);

        assertTrue(v2Schema.contains("update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"));
        assertTrue(v1Baseline.contains("update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"));
        assertTrue(v9Migration.contains("TABLE_NAME = 'auto_schedule_batch'"));
        assertTrue(v9Migration.contains("COLUMN_NAME = 'update_time'"));
        assertTrue(entity.contains("@TableField(\"update_time\")"));
    }

    @Test
    void createTableStatementsDoNotMixLegacyAndAtTimestampFamiliesInsideOneTable() throws IOException {
        String allSql = readAllSql();
        int legacyTimestampTables = 0;
        int atTimestampTables = 0;

        for (Set<String> tableColumns : timestampColumnsByCreateTable(allSql)) {
            boolean usesLegacyNames = tableColumns.contains("create_time") || tableColumns.contains("update_time");
            boolean usesAtNames = tableColumns.contains("created_at") || tableColumns.contains("updated_at");
            if (usesLegacyNames) {
                legacyTimestampTables++;
            }
            if (usesAtNames) {
                atTimestampTables++;
            }
            assertFalse(usesLegacyNames && usesAtNames,
                    "A CREATE TABLE statement mixes create_time/update_time with created_at/updated_at: " + tableColumns);
        }

        assertTrue(legacyTimestampTables >= 10, "Expected legacy create_time/update_time tables to remain visible");
        assertTrue(atTimestampTables >= 10, "Expected created_at/updated_at tables to remain visible");
    }

    private String readAllSql() throws IOException {
        try (Stream<Path> paths = Files.walk(DB_DIR)) {
            return paths
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }

    private List<Set<String>> timestampColumnsByCreateTable(String sql) {
        List<Set<String>> tables = new ArrayList<>();
        Set<String> currentColumns = null;

        for (String line : sql.split("\\R")) {
            String trimmed = line.stripLeading();
            if (trimmed.toUpperCase().startsWith("CREATE TABLE")) {
                currentColumns = new HashSet<>();
                continue;
            }
            if (currentColumns == null) {
                continue;
            }
            for (String timestampColumn : Set.of("create_time", "update_time", "created_at", "updated_at")) {
                if (trimmed.startsWith(timestampColumn + " ")) {
                    currentColumns.add(timestampColumn);
                }
            }
            if (trimmed.startsWith(")")) {
                if (!currentColumns.isEmpty()) {
                    tables.add(Set.copyOf(currentColumns));
                }
                currentColumns = null;
            }
        }

        return tables;
    }
}
