package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V4ScheduleRiskServiceTest {

    @Test
    void buildContextReusesLoadedTimeSlotsForTotalCount() throws IOException {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V4ScheduleRiskService.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("timeSlotMapper.selectCount"));
        assertTrue(source.contains("List<TimeSlot> slots = timeSlotMapper.selectList"));
        assertTrue(source.contains("context.totalTimeSlots = Math.max(slots.size(), 1)"));
    }
}
