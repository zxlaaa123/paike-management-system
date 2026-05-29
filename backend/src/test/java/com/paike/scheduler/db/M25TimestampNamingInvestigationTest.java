package com.paike.scheduler.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M25TimestampNamingInvestigationTest {

    private static final Path DB_DIR = Path.of("src", "main", "resources", "db");
    private static final Path ENTITY_DIR = Path.of("src", "main", "java", "com", "paike", "scheduler", "entity");
    private static final Set<String> TIMESTAMP_COLUMNS = Set.of("create_time", "update_time", "created_at", "updated_at");
    private static final Pattern TABLE_NAME = Pattern.compile("@TableName\\(\"([^\"]+)\"\\)");
    private static final Pattern TIMESTAMP_TABLE_FIELD =
            Pattern.compile("@TableField\\(\"(create_time|update_time|created_at|updated_at)\"\\)");

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
    void explicitEntityTimestampMappingsExistInCreateTableDefinitions() throws IOException {
        Map<String, Set<String>> tableColumns = timestampColumnsByTable(readAllSql());
        List<String> missingColumns = new ArrayList<>();

        for (Path entityPath : entityPaths()) {
            String source = Files.readString(entityPath, StandardCharsets.UTF_8);
            Matcher tableNameMatcher = TABLE_NAME.matcher(source);
            if (!tableNameMatcher.find()) {
                continue;
            }
            String tableName = tableNameMatcher.group(1);
            Set<String> mappedColumns = timestampTableFields(source);
            for (String mappedColumn : mappedColumns) {
                if (!tableColumns.getOrDefault(tableName, Set.of()).contains(mappedColumn)) {
                    missingColumns.add(entityPath.getFileName() + " -> " + tableName + "." + mappedColumn);
                }
            }
        }

        assertTrue(missingColumns.isEmpty(),
                "Timestamp columns mapped by entities must exist in CREATE TABLE definitions: " + missingColumns);
    }

    @Test
    void createTableStatementsDoNotMixLegacyAndAtTimestampFamiliesInsideOneTable() throws IOException {
        Map<String, Set<String>> tableColumnsByName = timestampColumnsByTable(readAllSql());
        int legacyTimestampTables = 0;
        int atTimestampTables = 0;

        for (Map.Entry<String, Set<String>> entry : tableColumnsByName.entrySet()) {
            Set<String> tableColumns = entry.getValue();
            boolean usesLegacyNames = tableColumns.contains("create_time") || tableColumns.contains("update_time");
            boolean usesAtNames = tableColumns.contains("created_at") || tableColumns.contains("updated_at");
            if (usesLegacyNames) {
                legacyTimestampTables++;
            }
            if (usesAtNames) {
                atTimestampTables++;
            }
            assertFalse(usesLegacyNames && usesAtNames,
                    "A CREATE TABLE statement mixes create_time/update_time with created_at/updated_at: "
                            + entry.getKey() + " " + tableColumns);
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

    private List<Path> entityPaths() throws IOException {
        try (Stream<Path> paths = Files.walk(ENTITY_DIR)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private Set<String> timestampTableFields(String source) {
        Set<String> columns = new HashSet<>();
        Matcher matcher = TIMESTAMP_TABLE_FIELD.matcher(source);
        while (matcher.find()) {
            columns.add(matcher.group(1));
        }
        return columns;
    }

    private Map<String, Set<String>> timestampColumnsByTable(String sql) {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        Pattern createTable = Pattern.compile(
                "^CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`?([a-zA-Z0-9_]+)`?\\s*\\(.*",
                Pattern.CASE_INSENSITIVE);
        String currentTable = null;
        Set<String> currentColumns = null;

        for (String line : sql.split("\\R")) {
            String trimmed = line.stripLeading();
            Matcher createTableMatcher = createTable.matcher(trimmed);
            if (createTableMatcher.matches()) {
                currentTable = createTableMatcher.group(1);
                currentColumns = new HashSet<>();
                continue;
            }
            if (currentColumns == null) {
                continue;
            }
            for (String timestampColumn : TIMESTAMP_COLUMNS) {
                if (trimmed.startsWith(timestampColumn + " ") || trimmed.startsWith("`" + timestampColumn + "` ")) {
                    currentColumns.add(timestampColumn);
                }
            }
            if (trimmed.startsWith(")")) {
                if (!currentColumns.isEmpty()) {
                    tables.computeIfAbsent(currentTable, ignored -> new HashSet<>()).addAll(currentColumns);
                }
                currentTable = null;
                currentColumns = null;
            }
        }

        return tables;
    }
}
