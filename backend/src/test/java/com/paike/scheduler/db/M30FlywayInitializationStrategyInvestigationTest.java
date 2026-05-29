package com.paike.scheduler.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M30FlywayInitializationStrategyInvestigationTest {

    @Test
    void projectDoesNotCarryFlywayRuntimeDependency() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);

        assertFalse(pom.contains("<artifactId>flyway-mysql</artifactId>"));
        assertFalse(pom.contains("<artifactId>flyway-core</artifactId>"));
    }

    @Test
    void applicationDocumentsSqlInitAsDatabaseInitializationEntry() throws IOException {
        String application = Files.readString(
                Path.of("src", "main", "resources", "application.yml"),
                StandardCharsets.UTF_8);
        String dbReadme = Files.readString(
                Path.of("src", "main", "resources", "db", "README.md"),
                StandardCharsets.UTF_8);

        assertFalse(application.contains("flyway:"));
        assertFalse(application.contains("enabled: false  # 自用项目"));
        assertTrue(application.contains("本项目不引入 Flyway/Liquibase"));
        assertTrue(application.contains("mode: always"));
        assertTrue(application.contains("schema-locations: classpath:db/schema.sql"));
        assertTrue(dbReadme.contains("本项目**有意不上 Flyway/Liquibase**"));
        assertTrue(dbReadme.contains("spring.sql.init"));
        assertTrue(dbReadme.contains("SemesterSchemaInitializer"));
    }
}
