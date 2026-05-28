package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3ScheduleGenerateServiceTest {

    @Test
    void candidateScoringUsesDeltaPenaltyWithoutDeadLegacyBranch() throws IOException {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V3ScheduleGenerateService.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("USE_DELTA_PENALTY_SCORING"));
        assertFalse(source.contains("scoreCandidateLegacy"));
        assertTrue(source.contains("DeltaPenaltyScorer.weightedSoftDeltaPenalty"));
    }
}
