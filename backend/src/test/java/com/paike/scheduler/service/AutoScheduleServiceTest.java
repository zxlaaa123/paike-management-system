package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoScheduleServiceTest {

    @Test
    void failureReasonCategoryUsesStructuredConflictTag() throws IOException {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "AutoScheduleService.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("ScheduleConflictService.extractReasonType(reason)"));
        assertFalse(source.contains("reason.contains(\"教师禁排\")"));
        assertFalse(source.contains("reason.contains(\"已有课程\")"));
        assertFalse(source.contains("reason.contains(\"容量\")"));
    }
}
