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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M14MapStringObjectUsageInvestigationTest {

    private static final Pattern MAP_STRING_OBJECT = Pattern.compile("Map\\s*<\\s*String\\s*,\\s*Object\\s*>");

    @Test
    void mapStringObjectUsageIsBroadAndContractFacing() throws IOException {
        // 基线随 M-14 分阶段收敛逐步下调（棘轮）。
        // 阶段1（2026-06-03）：低风险端点改 VO，移除 9 行 / 5 个文件清零（Health/Auth/ScheduleScore/Schedule[checkConflict]/SchedulePlanExplain）。
        //   66→57 处、16→11 文件、controller 21→14、service 45→43。
        // 阶段2（2026-06-03）：apply/adjust/rollback/compare 改 VO，移除 25 行 / 6 个文件清零
        //   （SchedulePlanService/ScheduleCompareService/V4ScheduleAdjustmentService/V5SimulationService/SchedulePlanController[部分]/SchedulePlanItemController/V5SimulationController）。
        //   57→32 处、11→4 文件、controller 14→9、service 43→23。剩余 4 文件均为阶段3 目标（ScheduleStatisticsService/Controller + Analysis/Risk 两个 refresh 端点）。
        List<Hit> hits = collectHits();
        Map<String, Long> byFile = countByFile(hits);

        assertEquals(32, hits.size());
        assertEquals(4, byFile.size());
        assertEquals(9, hits.stream().filter(hit -> hit.path().contains("/controller/")).count());
        assertEquals(23, hits.stream().filter(hit -> hit.path().contains("/service/")).count());

        // 阶段3 的真正难点：统计聚合 23 处仍在。
        assertEquals(23, byFile.get("backend/src/main/java/com/paike/scheduler/service/ScheduleStatisticsService.java"));
        // 阶段2 已清零，不应再出现在分布里。
        assertFalse(byFile.containsKey("backend/src/main/java/com/paike/scheduler/service/SchedulePlanService.java"));
        assertFalse(byFile.containsKey("backend/src/main/java/com/paike/scheduler/service/ScheduleCompareService.java"));
    }

    @Test
    void publicControllerContractsStillExposeMapStringObject() throws IOException {
        List<Hit> controllerContracts = collectHits().stream()
                .filter(hit -> hit.path().contains("/controller/"))
                .filter(hit -> hit.line().contains("public Result<"))
                .toList();

        // 阶段1 移除 6 个公开端点契约（health/logout/checkConflict/getScoreSummary/rescore/getUnassignedSummary）：18→12。
        // 阶段2 再移除 5 个（compare/apply/rollback/adjust/v5-apply）：12→7，剩 Statistics 5 + Analysis/Risk 各 1。
        assertEquals(7, controllerContracts.size());
        assertTrue(controllerContracts.stream().anyMatch(hit -> hit.path().endsWith("ScheduleStatisticsController.java")));
        assertFalse(controllerContracts.stream().anyMatch(hit -> hit.path().endsWith("SchedulePlanController.java")));
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

