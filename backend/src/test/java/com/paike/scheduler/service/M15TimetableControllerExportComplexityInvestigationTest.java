package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M15TimetableControllerExportComplexityInvestigationTest {

    @Test
    void timetableControllerStillMixesQueryAssemblyAndExcelExportResponsibilities() throws IOException {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "controller", "TimetableController.java"),
                StandardCharsets.UTF_8);

        List<String> methodNames = Pattern.compile("^\\s*(public|private)\\s+[^\\n]*?\\s+(\\w+)\\s*\\(", Pattern.MULTILINE)
                .matcher(source)
                .results()
                .map(match -> match.group(2))
                .toList();

        assertEquals(6, count(source, "@GetMapping"));
        assertEquals(28, methodNames.size());
        assertEquals(11, methodNames.stream().filter(name -> name.matches(
                "export.*|createTimetableSheet|buildFileName|sanitizeFileName|encodeFileName|buildCellText|appendIfPresent|defaultString")).count());
        assertEquals(6, methodNames.stream().filter(name -> name.matches(
                "querySchedulesByTaskField|queryBy.*|toTimetableVos|buildTimetableVo")).count());
        assertEquals(7, count(source, "private\\s+final\\s+\\w*Mapper\\s+\\w+;"));

        assertTrue(source.contains("exportClassTimetable"));
        assertTrue(source.contains("exportTeacherTimetable"));
        assertTrue(source.contains("exportClassroomTimetable"));
        assertTrue(source.contains("createTimetableSheet"));
        assertTrue(source.contains("buildCellText"));
    }

    private int count(String source, String regex) {
        return (int) Pattern.compile(regex).matcher(source).results().count();
    }

}
