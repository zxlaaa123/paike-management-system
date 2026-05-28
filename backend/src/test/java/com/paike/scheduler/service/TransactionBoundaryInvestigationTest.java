package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionBoundaryInvestigationTest {

    @Test
    void autoScheduleAndV3GenerateStayAtTopLevelTransactionalBoundary() throws IOException {
        String autoSchedule = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "AutoScheduleService.java"),
                StandardCharsets.UTF_8);
        String v3Generate = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V3ScheduleGenerateService.java"),
                StandardCharsets.UTF_8);

        assertTrue(autoSchedule.contains("@Transactional(rollbackFor = Exception.class)"));
        assertTrue(autoSchedule.contains("public AutoScheduleResult run(AutoScheduleRequest request)"));
        assertTrue(autoSchedule.contains("return finalizeBatch(batch, targetTasks.size(), stats);"));
        assertTrue(autoSchedule.contains("saveSchedule(task, attempt.slot(), attempt.room(), batch.getId());"));
        assertFalse(autoSchedule.contains("TransactionTemplate"));

        assertTrue(v3Generate.contains("@Transactional(rollbackFor = Exception.class)"));
        assertTrue(v3Generate.contains("public ScheduleGenerateResult generate(ScheduleGenerateRequest request)"));
        assertTrue(v3Generate.contains("public List<ScheduleGenerateResult> generateMultiple(MultipleScheduleGenerateRequest request)"));
        assertTrue(v3Generate.contains("results.add(generate(single));"));
        assertTrue(v3Generate.contains("scoreService.rescore(plan);"));
        assertFalse(v3Generate.contains("TransactionTemplate"));
    }
}
