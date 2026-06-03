package com.paike.scheduler.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M16TableFieldViewFieldsInvestigationTest {

    @Test
    void tableFieldExistFalseViewFieldsRemainOnEntities() throws IOException {
        List<FieldHit> hits = collectHits();
        Map<String, Long> byEntity = countByEntity(hits);

        assertEquals(29, hits.size());
        assertEquals(5, byEntity.size());
        assertEquals(10, byEntity.get("Schedule"));
        assertEquals(8, byEntity.get("TeachingTask"));
        assertEquals(5, byEntity.get("SchedulePlanItem"));
        assertEquals(4, byEntity.get("UnscheduledTask"));
        assertEquals(2, byEntity.get("SchedulePlan"));
    }

    @Test
    void mapperAliasesStillPopulateSomeViewFields() throws IOException {
        String teachingTaskMapper = source("src/main/resources/mapper/TeachingTaskMapper.xml");
        String unscheduledTaskMapper = source("src/main/resources/mapper/UnscheduledTaskMapper.xml");

        assertTrue(teachingTaskMapper.contains("c.course_type AS courseType"));
        assertTrue(teachingTaskMapper.contains("t.name AS teacherName"));
        assertTrue(teachingTaskMapper.contains("cl.class_name AS className"));
        assertTrue(teachingTaskMapper.contains("cl.student_count AS studentCount"));

        assertTrue(unscheduledTaskMapper.contains("c.course_name AS courseName"));
        assertTrue(unscheduledTaskMapper.contains("t.name AS teacherName"));
        assertTrue(unscheduledTaskMapper.contains("cl.class_name AS className"));
        assertTrue(unscheduledTaskMapper.contains("b.batch_no AS batchNo"));
    }

    private List<FieldHit> collectHits() throws IOException {
        Path entityRoot = resolveEntityRoot();
        try (Stream<Path> stream = Files.walk(entityRoot)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            String entityName = path.getFileName().toString().replace(".java", "");
                            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                            Stream.Builder<FieldHit> builder = Stream.builder();
                            for (int i = 0; i < lines.size(); i++) {
                                if (!"@TableField(exist = false)".equals(lines.get(i).strip())) {
                                    continue;
                                }
                                for (int j = i + 1; j < lines.size(); j++) {
                                    String line = lines.get(j).strip();
                                    if (line.startsWith("private ")) {
                                        builder.add(new FieldHit(entityName, line));
                                        break;
                                    }
                                }
                            }
                            return builder.build();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }
    }

    private Map<String, Long> countByEntity(List<FieldHit> hits) {
        Map<String, Long> byEntity = new LinkedHashMap<>();
        for (FieldHit hit : hits) {
            byEntity.merge(hit.entityName(), 1L, Long::sum);
        }
        return byEntity;
    }

    private Path resolveEntityRoot() {
        Path direct = Path.of("src/main/java/com/paike/scheduler/entity");
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("backend/src/main/java/com/paike/scheduler/entity");
    }

    private String source(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct, StandardCharsets.UTF_8);
        }

        Path fromRoot = Path.of("backend").resolve(relativePath);
        return Files.readString(fromRoot, StandardCharsets.UTF_8);
    }

    private record FieldHit(String entityName, String declaration) {
    }
}

