package com.paike.scheduler.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M41DeletedZeroConditionCleanupTest {

    private static final Pattern JAVA_DELETED_ZERO_CONDITION =
            Pattern.compile("\\.eq\\s*\\([^\\n]*::getDeleted\\s*,\\s*0\\s*\\)");

    @Test
    void javaLambdaWrappersNoLongerRepeatDeletedZeroConditions() throws IOException {
        List<SourceFile> offenders = sourceFiles(resolveMainJavaRoot(), ".java").stream()
                .filter(source -> JAVA_DELETED_ZERO_CONDITION.matcher(source.content()).find())
                .toList();

        assertEquals(0, offenders.size(), offenders::toString);
    }

    @Test
    void handwrittenSqlStillKeepsExplicitDeletedConditions() throws IOException {
        String scheduleMapper = source("src/main/resources/mapper/ScheduleMapper.xml");
        String teachingTaskMapper = source("src/main/resources/mapper/TeachingTaskMapper.xml");
        String baselineSql = source("src/main/resources/db/migration/V1__baseline.sql");

        assertTrue(scheduleMapper.contains("WHERE s.deleted = 0"));
        assertTrue(scheduleMapper.contains("tt.deleted = 0"));
        assertTrue(teachingTaskMapper.contains("WHERE tt.deleted = 0"));
        assertTrue(teachingTaskMapper.contains("c.deleted = 0"));
        assertTrue(baselineSql.contains("CASE WHEN deleted = 0"));
    }

    @Test
    void coreDeletedFieldsRemainTableLogicManaged() throws IOException {
        assertTrue(source("src/main/java/com/paike/scheduler/entity/Schedule.java").contains("@TableLogic"));
        assertTrue(source("src/main/java/com/paike/scheduler/entity/TeachingTask.java").contains("@TableLogic"));
        assertTrue(source("src/main/java/com/paike/scheduler/entity/Classroom.java").contains("@TableLogic"));
        assertTrue(source("src/main/java/com/paike/scheduler/entity/TeacherUnavailableTime.java").contains("@TableLogic"));
    }

    private List<SourceFile> sourceFiles(Path root, String extension) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.toString().endsWith(extension))
                    .map(path -> {
                        try {
                            return new SourceFile(
                                    root.relativize(path).toString().replace('\\', '/'),
                                    Files.readString(path, StandardCharsets.UTF_8)
                            );
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }
    }

    private Path resolveMainJavaRoot() {
        Path direct = Path.of("src/main/java");
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("backend/src/main/java");
    }

    private String source(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct, StandardCharsets.UTF_8);
        }

        Path fromRoot = Path.of("backend").resolve(relativePath);
        return Files.readString(fromRoot, StandardCharsets.UTF_8);
    }

    private record SourceFile(String path, String content) {
    }
}

