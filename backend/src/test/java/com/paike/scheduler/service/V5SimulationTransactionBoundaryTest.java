package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class V5SimulationTransactionBoundaryTest {

    @Test
    void generateAndLocalReplanBuildDetailAfterWriteTransaction() throws IOException {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V5SimulationService.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("Long simulationPlanId = runInTransaction(() -> generateInTransaction(taskId, suggestionId));"));
        assertTrue(source.contains("V5SimulationPlanDetailVo detail = detail(taskId, simulationPlanId);"));
        assertTrue(source.contains("return detail;"));
        assertTrue(source.contains("LocalReplanResult result = runInTransaction(() -> localReplanInTransaction(taskId, request));"));
        assertTrue(source.contains("V5SimulationPlanDetailVo detail = detail(taskId, result.planId());"));
        assertTrue(source.contains("return new TransactionTemplate(transactionManager).execute(status -> action.get());"));
    }
}
