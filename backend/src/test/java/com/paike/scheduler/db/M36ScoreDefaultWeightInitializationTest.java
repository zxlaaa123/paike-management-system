package com.paike.scheduler.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M36ScoreDefaultWeightInitializationTest {

    @Test
    void semesterScriptPromotesExistingLatestSemesterWhenNoCurrentSemesterExists() throws IOException {
        String semester = resource("db/v3_semester.sql");

        assertTrue(semester.contains("如果已存在学期但没有任何当前学期"));
        assertTrue(semester.contains("UPDATE semester"));
        assertTrue(semester.contains("SET is_current = 1"));
        assertTrue(semester.contains("NOT EXISTS"));
        assertTrue(semester.contains("WHERE deleted = 0"));
        assertTrue(semester.contains("AND is_current = 1"));
        assertTrue(semester.contains("ORDER BY updated_at DESC, id DESC"));
    }

    @Test
    void scoreScriptRunsAfterSemesterFallbackAndStillTargetsCurrentSemester() throws IOException {
        String application = resource("application.yml");
        String score = resource("db/v3_score.sql");

        assertTrue(application.indexOf("classpath:db/v3_semester.sql")
                        < application.indexOf("classpath:db/v3_score.sql"),
                "v3_semester.sql must run before v3_score.sql");
        assertTrue(count(score, "FROM semester s WHERE s.is_current = 1") >= 30,
                "default score weights must continue targeting the current semester");
    }

    @Test
    void flywayBaselineContainsSameCurrentSemesterFallbackBeforeScoreWeights() throws IOException {
        String baseline = resource("db/migration/V1__baseline.sql");

        int fallbackIndex = baseline.indexOf("如果已存在学期但没有任何当前学期");
        int scoreWeightIndex = baseline.indexOf("INSERT INTO schedule_rule_weight");
        assertTrue(fallbackIndex > 0, "baseline must contain current semester fallback");
        assertTrue(scoreWeightIndex > fallbackIndex,
                "baseline must ensure current semester before inserting default rule weights");
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
