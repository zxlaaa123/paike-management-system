package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class M13ControllerMapperInjectionInvestigationTest {

    private static final Pattern MAPPER_FIELD_PATTERN =
            Pattern.compile("private\\s+final\\s+(\\w*Mapper)\\s+(\\w+);");

    @Test
    void controllerMapperInjectionScopeIsLimitedButReal() throws IOException {
        Map<String, Integer> mapperFieldCounts = new LinkedHashMap<>();

        countMapperFields("ScheduleController.java", mapperFieldCounts);
        countMapperFields("TeachingTaskController.java", mapperFieldCounts);
        countMapperFields("TimeSlotController.java", mapperFieldCounts);
        countMapperFields("TimetableController.java", mapperFieldCounts);

        assertEquals(3, mapperFieldCounts.size());
        assertEquals(8, mapperFieldCounts.get("ScheduleController.java"));
        assertEquals(5, mapperFieldCounts.get("TeachingTaskController.java"));
        assertEquals(1, mapperFieldCounts.get("TimeSlotController.java"));
        assertFalse(mapperFieldCounts.containsKey("TimetableController.java"));
        assertEquals(14, mapperFieldCounts.values().stream().mapToInt(Integer::intValue).sum());
    }

    private void countMapperFields(String fileName, Map<String, Integer> mapperFieldCounts) throws IOException {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "controller", fileName),
                StandardCharsets.UTF_8);

        Matcher matcher = MAPPER_FIELD_PATTERN.matcher(source);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        if (count > 0) {
            mapperFieldCounts.put(fileName, count);
        }
    }
}
