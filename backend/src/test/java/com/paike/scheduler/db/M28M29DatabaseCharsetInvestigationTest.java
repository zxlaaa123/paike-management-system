package com.paike.scheduler.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M28M29DatabaseCharsetInvestigationTest {

    private static final Path DB_DIR = Path.of("src", "main", "resources", "db");

    @Test
    void jdbcDefaultsUseJavaUtf8EncodingName() throws IOException {
        String application = Files.readString(
                Path.of("src", "main", "resources", "application.yml"),
                StandardCharsets.UTF_8);
        String envExample = Files.readString(Path.of(".env.example"), StandardCharsets.UTF_8);

        assertTrue(application.contains("characterEncoding=UTF-8"));
        assertTrue(envExample.contains("characterEncoding=UTF-8"));
        assertFalse(application.contains("characterEncoding=utf8"));
        assertFalse(envExample.contains("characterEncoding=utf8"));
    }

    @Test
    void createTableStatementsDeclareUtf8mb4CharsetAndCollation() throws IOException {
        String allSql = readAllSql();
        List<String> missingTableOptions = new ArrayList<>();
        int createTableCount = 0;
        StringBuilder statement = null;

        for (String line : allSql.split("\\R")) {
            if (line.stripLeading().toUpperCase().startsWith("CREATE TABLE")) {
                statement = new StringBuilder(line).append('\n');
                createTableCount++;
                continue;
            }

            if (statement == null) {
                continue;
            }

            statement.append(line).append('\n');
            if (line.stripLeading().startsWith(")")) {
                String createTable = statement.toString();
                if (!createTable.toUpperCase().contains("DEFAULT CHARSET=UTF8MB4")
                        || !createTable.toUpperCase().contains("COLLATE=UTF8MB4_UNICODE_CI")) {
                    missingTableOptions.add(firstCreateTableLine(createTable));
                }
                statement = null;
            }
        }

        assertTrue(createTableCount >= 30, "Expected SQL scripts to contain baseline CREATE TABLE statements");
        assertTrue(missingTableOptions.isEmpty(),
                "CREATE TABLE statements missing utf8mb4 table options: " + missingTableOptions);
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

    private String firstCreateTableLine(String statement) {
        for (String line : statement.split("\\R")) {
            if (line.toUpperCase().contains("CREATE TABLE")) {
                return line.trim();
            }
        }
        return statement.strip().lines().findFirst().orElse("<empty>");
    }
}
