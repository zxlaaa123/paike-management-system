package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M50RequestBodyOptionalityInvestigationTest {

    @Test
    void optionalRequestBodiesAreIntentionalDefaultsNotHiddenNullBugs() throws IOException {
        String repairTaskController = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "controller", "V5RepairTaskController.java"),
                StandardCharsets.UTF_8);
        String repairTaskService = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V5RepairTaskFlowService.java"),
                StandardCharsets.UTF_8);
        String reportController = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "controller", "ScheduleReportController.java"),
                StandardCharsets.UTF_8);
        String replanController = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "controller", "ScheduleReplanController.java"),
                StandardCharsets.UTF_8);
        String aiController = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "controller", "ScheduleAiAnalysisController.java"),
                StandardCharsets.UTF_8);
        String replanService = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V4ScheduleReplanService.java"),
                StandardCharsets.UTF_8);
        String aiService = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V4ScheduleAiAnalysisService.java"),
                StandardCharsets.UTF_8);
        String reportService = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V4ScheduleReportService.java"),
                StandardCharsets.UTF_8);
        String simulationService = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V5SimulationService.java"),
                StandardCharsets.UTF_8);

        assertTrue(repairTaskController.contains("@PostMapping(\"/{taskId}/cancel\")"));
        assertTrue(repairTaskController.contains("@Valid @RequestBody(required = false) V5RepairTaskCancelRequest request"));
        assertTrue(repairTaskController.contains("String reason = request == null ? null : request.getReason();"));
        assertTrue(repairTaskService.contains("public V5RepairTaskDetailVo cancelTask(Long taskId, String cancelReason)"));
        assertTrue(repairTaskService.contains("task.setCancelReason(trimToNull(cancelReason));"));

        assertTrue(reportController.contains("@Valid @RequestBody(required = false) V4ScheduleReportGenerateRequest request"));
        assertTrue(reportService.contains("request == null ? null : request.getReportType()"));
        assertTrue(reportService.contains("request == null || request.getIncludeCharts() == null || request.getIncludeCharts()"));
        assertTrue(replanController.contains("@Valid @RequestBody(required = false) V4ScheduleReplanRequest request"));
        assertTrue(replanService.contains("V4ScheduleReplanRequest safeRequest = request == null ? new V4ScheduleReplanRequest() : request;"));
        assertTrue(aiController.contains("@Valid @RequestBody(required = false) V4ScheduleAiAnalysisRequest request"));
        assertTrue(aiService.contains("request == null ? null : request.getAnalysisType()"));
        assertTrue(aiService.contains("request == null || request.getIncludeRisks() == null || request.getIncludeRisks()"));
        assertTrue(simulationService.contains("public V5SimulationPlanDetailVo localReplan(Long taskId, V5LocalReplanRequest request)"));
        assertTrue(simulationService.contains("V5LocalReplanRequest safeRequest = request == null ? new V5LocalReplanRequest() : request;"));

        assertFalse(repairTaskService.contains("request == null ? throw"));
    }
}
