package com.paike.scheduler.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M32ForeignKeyConstraintInvestigationTest {

    private static final Path DB_DIR = Path.of("src", "main", "resources", "db");
    private static final Pattern FOREIGN_KEY_PATTERN = Pattern.compile(
            "\\bFOREIGN\\s+KEY\\b|\\bREFERENCES\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`?([a-zA-Z0-9_]+)`?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "(?im)^\\s*`?([a-zA-Z][a-zA-Z0-9_]*)`?\\s+"
                    + "(BIGINT|INT|INTEGER|VARCHAR|TEXT|DATETIME|DATE|DECIMAL|TINYINT|BOOLEAN|TIMESTAMP|DOUBLE|LONGTEXT|JSON)\\b");

    @Test
    void sqlScriptsDoNotDeclareDatabaseForeignKeys() throws IOException {
        String allSql = readAllSql();

        assertFalse(FOREIGN_KEY_PATTERN.matcher(allSql).find(),
                "SQL initialization scripts currently do not declare database-level foreign keys");
    }

    @Test
    void sqlStillContainsManyRelationLikeColumnsWithoutForeignKeyConstraints() throws IOException {
        String allSql = readAllSql();
        Map<String, Set<String>> relationColumnsByTable = relationColumnsByTable(allSql);
        Set<String> relationColumns = relationColumnsByTable.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(relationColumnsByTable.size() >= 20,
                "Expected many tables to carry relation-like *_id columns");
        assertTrue(relationColumnsByTable.values().stream().mapToInt(Set::size).sum() >= 80,
                "Expected many relation-like columns to exist without database FK constraints");

        assertTrue(relationColumns.containsAll(Set.of(
                "semester_id",
                "plan_id",
                "teaching_task_id",
                "teacher_id",
                "course_id",
                "class_id",
                "time_slot_id",
                "classroom_id"
        )));

        assertTrue(relationColumnsByTable.getOrDefault("schedule", Set.of()).containsAll(Set.of(
                "teaching_task_id",
                "course_id",
                "teacher_id",
                "class_id",
                "time_slot_id",
                "classroom_id"
        )));
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

    private Map<String, Set<String>> relationColumnsByTable(String sql) {
        Map<String, Set<String>> result = new TreeMap<>();
        String tableName = null;
        Set<String> relationColumns = new TreeSet<>();

        for (String line : sql.split("\\R")) {
            var tableMatcher = CREATE_TABLE_PATTERN.matcher(line);
            if (tableMatcher.find()) {
                tableName = tableMatcher.group(1);
                relationColumns = new TreeSet<>();
                continue;
            }

            if (tableName == null) {
                continue;
            }

            var columnMatcher = COLUMN_PATTERN.matcher(line);
            if (columnMatcher.find()) {
                String column = columnMatcher.group(1);
                if (!"id".equalsIgnoreCase(column) && column.toLowerCase().endsWith("_id")) {
                    relationColumns.add(column);
                }
            }

            if (line.trim().startsWith(")")) {
                if (!relationColumns.isEmpty()) {
                    result.put(tableName, relationColumns);
                }
                tableName = null;
                relationColumns = new TreeSet<>();
            }
        }
        return result;
    }
}
