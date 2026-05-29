package com.paike.scheduler.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M14MapStringObjectUsageInvestigationTest {

    private static final Pattern MAP_STRING_OBJECT = Pattern.compile("Map\\s*<\\s*String\\s*,\\s*Object\\s*>");

    @Test
    void mapStringObjectUsageIsBroadAndContractFacing() throws IOException {
        List<Hit> hits = collectHits();
        Map<String, Long> byFile = countByFile(hits);

        assertEquals(66, hits.size());
        assertEquals(16, byFile.size());
        assertEquals(21, hits.stream().filter(hit -> hit.path().contains("/controller/")).count());
        assertEquals(45, hits.stream().filter(hit -> hit.path().contains("/service/")).count());

        assertEquals(23, byFile.get("backend/src/main/java/com/paike/scheduler/service/ScheduleStatisticsService.java"));
        assertEquals(10, byFile.get("backend/src/main/java/com/paike/scheduler/service/SchedulePlanService.java"));
        assertEquals(7, byFile.get("backend/src/main/java/com/paike/scheduler/service/ScheduleCompareService.java"));
    }

    @Test
    void publicControllerContractsStillExposeMapStringObject() throws IOException {
        List<Hit> controllerContracts = collectHits().stream()
                .filter(hit -> hit.path().contains("/controller/"))
                .filter(hit -> hit.line().contains("public Result<"))
                .toList();

        assertEquals(18, controllerContracts.size());
        assertTrue(controllerContracts.stream().anyMatch(hit -> hit.path().endsWith("ScheduleStatisticsController.java")));
        assertTrue(controllerContracts.stream().anyMatch(hit -> hit.path().endsWith("SchedulePlanController.java")));
    }

    private List<Hit> collectHits() throws IOException {
        Path sourceRoot = resolveSourceRoot();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            String relativePath = normalize(sourceRoot.relativize(path));
                            String source = Files.readString(path, StandardCharsets.UTF_8);
                            return source.lines()
                                    .filter(line -> {
                                        Matcher matcher = MAP_STRING_OBJECT.matcher(line);
                                        return matcher.find();
                                    })
                                    .map(line -> new Hit("backend/src/main/java/" + relativePath, line.strip()));
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }
    }

    private Map<String, Long> countByFile(List<Hit> hits) {
        Map<String, Long> byFile = new HashMap<>();
        for (Hit hit : hits) {
            byFile.merge(hit.path(), 1L, Long::sum);
        }
        return byFile;
    }

    private Path resolveSourceRoot() {
        Path direct = Path.of("src/main/java");
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("backend/src/main/java");
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record Hit(String path, String line) {
    }
}

