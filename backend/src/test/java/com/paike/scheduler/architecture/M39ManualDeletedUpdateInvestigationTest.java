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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M39ManualDeletedUpdateInvestigationTest {

    private static final Pattern SET_DELETED_TO_ONE = Pattern.compile("\\.setDeleted\\s*\\(\\s*1\\s*\\)");
    private static final Pattern WRAPPER_SET_DELETED_TO_ONE = Pattern.compile("\\.set\\s*\\(\\s*Schedule::getDeleted\\s*,\\s*1\\s*\\)");

    @Test
    void mainCodeDoesNotSetDeletedToOneManually() throws IOException {
        List<SourceFile> offenders = sourceFiles().stream()
                .filter(source -> SET_DELETED_TO_ONE.matcher(source.content()).find()
                        || WRAPPER_SET_DELETED_TO_ONE.matcher(source.content()).find())
                .toList();

        assertEquals(0, offenders.size(), offenders::toString);
    }

    @Test
    void schedulePlanServiceRetiresOldAppliedSchedulesThroughLogicDelete() throws IOException {
        String source = source("src/main/java/com/paike/scheduler/service/SchedulePlanService.java");

        assertTrue(source.contains("scheduleMapper.delete(new LambdaQueryWrapper<Schedule>()"));
        assertTrue(source.contains(".eq(Schedule::getSemesterId, semesterId)"));
        assertTrue(source.contains(".eq(Schedule::getPlanId, oldPlan.getId())"));
        assertFalse(source.contains("new LambdaUpdateWrapper<Schedule>()"));
        assertFalse(WRAPPER_SET_DELETED_TO_ONE.matcher(source).find());
    }

    private List<SourceFile> sourceFiles() throws IOException {
        Path root = resolveMainJavaRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
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

