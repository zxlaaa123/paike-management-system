package com.paike.scheduler.service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M31LogicDeleteEntityCoverageInvestigationTest {

    private static final Path ENTITY_DIR = Path.of("src", "main", "java", "com", "paike", "scheduler", "entity");
    private static final Path DB_DIR = Path.of("src", "main", "resources", "db");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("@TableName\\(\"([^\"]+)\"\\)");
    private static final Pattern DELETED_FIELD_PATTERN = Pattern.compile("\\bprivate\\s+\\w+(?:<[^>]+>)?\\s+deleted\\s*;");

    @Test
    void globalLogicDeleteConfigOnlyAppliesToEntitiesThatActuallyMapDeletedField() throws IOException {
        String application = Files.readString(
                Path.of("src", "main", "resources", "application.yml"),
                StandardCharsets.UTF_8);
        assertTrue(application.contains("logic-delete-field: deleted"));

        Map<String, String> tableByEntity = new TreeMap<>();
        Map<String, String> sourceByEntity = new TreeMap<>();
        try (Stream<Path> paths = Files.list(ENTITY_DIR)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                var matcher = TABLE_NAME_PATTERN.matcher(source);
                if (matcher.find()) {
                    String entityName = path.getFileName().toString().replace(".java", "");
                    tableByEntity.put(entityName, matcher.group(1));
                    sourceByEntity.put(entityName, source);
                }
            }
        }

        Map<String, String> missingDeleted = new TreeMap<>();
        for (Map.Entry<String, String> entry : tableByEntity.entrySet()) {
            String entityName = entry.getKey();
            String source = sourceByEntity.get(entityName);
            boolean hasDeletedField = DELETED_FIELD_PATTERN.matcher(source).find();
            if (hasDeletedField) {
                assertTrue(source.contains("@TableLogic"), entityName + " maps deleted but is not annotated with @TableLogic");
            } else {
                missingDeleted.put(entityName, entry.getValue());
            }
        }

        assertEquals(Set.of(
                "AutoScheduleBatch",
                "ScheduleCandidatePosition",
                "ScheduleConflictReport",
                "ScheduleConsistencyCheck",
                "ScheduleGenerateLog",
                "ScheduleOptimizationCompare",
                "ScheduleRegressionTest",
                "ScheduleRepairSuggestion",
                "ScheduleRepairTask",
                "ScheduleRuleConfig",
                "ScheduleRuleWeight",
                "ScheduleScoreReport",
                "SystemAuditLog",
                "TimeSlot",
                "UnscheduledTask"
        ), new TreeSet<>(missingDeleted.keySet()));

        String sql = readAllSql();
        for (Map.Entry<String, String> entry : missingDeleted.entrySet()) {
            assertFalse(tableStatementsMentionDeleted(sql, entry.getValue()),
                    entry.getKey() + " does not map deleted, but SQL table " + entry.getValue() + " mentions deleted");
        }
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

    private boolean tableStatementsMentionDeleted(String sql, String tableName) {
        Pattern tablePattern = Pattern.compile("(?i)(`" + Pattern.quote(tableName) + "`|\\b" + Pattern.quote(tableName) + "\\b)");
        for (String statement : sql.split(";")) {
            if (tablePattern.matcher(statement).find()
                    && Pattern.compile("\\bdeleted\\b", Pattern.CASE_INSENSITIVE).matcher(statement).find()) {
                return true;
            }
        }
        return false;
    }
}
