package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V5ClassroomBatchLoadingTest {

    @Test
    void repairSuggestionVoLoadsClassroomsInOneBatch() throws IOException {
        String source = source("V5RepairSuggestionService.java");

        assertFalse(source.contains("classroomMapper.selectById("));
        assertEquals(1, count(source, "classroomMapper.selectBatchIds("));
        assertTrue(source.contains("loadClassroomNames(list)"));
        assertTrue(source.contains("toVo(s, classroomNames)"));
    }

    @Test
    void simulationCompareLoadsClassroomsInOneBatch() throws IOException {
        String source = source("V5SimulationService.java");

        assertFalse(source.contains("classroomMapper.selectById("));
        assertEquals(1, count(source, "classroomMapper.selectBatchIds("));
        assertTrue(source.contains("loadClassroomNames(baselineItems, simulationItems, before, after)"));
        assertTrue(source.contains("buildChangedItems(baselineTaskMap, simulationTaskMap, before, after, classroomNames)"));
        assertTrue(source.contains("buildRoomUtilizationChanges(baselineItems, simulationItems, classroomNames)"));
    }

    private String source(String fileName) throws IOException {
        Path path = Path.of("src", "main", "java", "com", "paike", "scheduler", "service", fileName);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private int count(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
