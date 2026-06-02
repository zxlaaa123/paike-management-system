package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M15TimetableControllerExportComplexityInvestigationTest {

    @Test
    void timetableControllerDelegatesQueryAssemblyAndExcelExportResponsibilities() throws IOException {
        String controllerSource = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "controller", "TimetableController.java"),
                StandardCharsets.UTF_8);
        String serviceSource = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "TimetableService.java"),
                StandardCharsets.UTF_8);

        List<String> controllerMethodNames = Pattern.compile("^\\s*(public|private)\\s+[^\\n]*?\\s+(\\w+)\\s*\\(", Pattern.MULTILINE)
                .matcher(controllerSource)
                .results()
                .map(match -> match.group(2))
                .toList();

        assertEquals(6, count(controllerSource, "@GetMapping"));
        assertEquals(6, controllerMethodNames.size());
        assertEquals(0, count(controllerSource, "private\\s+final\\s+\\w*Mapper\\s+\\w+;"));
        assertTrue(controllerSource.contains("private final TimetableService timetableService;"));
        assertFalse(controllerSource.contains("createTimetableSheet"));
        assertFalse(controllerSource.contains("buildCellText"));
        assertFalse(controllerSource.contains("XSSFWorkbook"));

        assertEquals(7, count(serviceSource, "private\\s+final\\s+\\w*Mapper\\s+\\w+;"));
        assertTrue(serviceSource.contains("public List<TimetableVo> listClassTimetable"));
        assertTrue(serviceSource.contains("public List<TimetableVo> listTeacherTimetable"));
        assertTrue(serviceSource.contains("public List<TimetableVo> listClassroomTimetable"));
        assertTrue(serviceSource.contains("public void exportClassTimetable"));
        assertTrue(serviceSource.contains("public void exportTeacherTimetable"));
        assertTrue(serviceSource.contains("public void exportClassroomTimetable"));
        assertTrue(serviceSource.contains("createTimetableSheet"));
        assertTrue(serviceSource.contains("buildCellText"));
        assertTrue(serviceSource.contains("querySchedulesByTaskField"));

    }

    private int count(String source, String regex) {
        return (int) Pattern.compile(regex).matcher(source).results().count();
    }

}
