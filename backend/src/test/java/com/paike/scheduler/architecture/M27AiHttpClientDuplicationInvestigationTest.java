package com.paike.scheduler.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M27AiHttpClientDuplicationInvestigationTest {

    @Test
    void remoteAiHttpClientUsageIsLimitedToTwoServices() throws IOException {
        List<SourceFile> httpClientUsers = sourceFiles().stream()
                .filter(source -> source.content().contains("java.net.http.HttpClient"))
                .toList();

        assertEquals(2, httpClientUsers.size());
        assertTrue(httpClientUsers.stream().anyMatch(source -> source.path().endsWith("V4ScheduleAiAnalysisService.java")));
        assertTrue(httpClientUsers.stream().anyMatch(source -> source.path().endsWith("V5RepairExplanationService.java")));
    }

    @Test
    void servicesAlreadyShareHttpClientPerClassButDuplicateAiConfiguration() throws IOException {
        Map<String, String> serviceSources = aiServiceSources();

        for (String source : serviceSources.values()) {
            assertTrue(source.contains("private static final HttpClient HTTP_CLIENT"));
            assertTrue(source.contains("HttpClient.newBuilder()"));
            assertTrue(source.contains("HTTP_CLIENT.send("));
            assertTrue(source.contains("@Value(\"${app.ai.api-key:}\")"));
            assertTrue(source.contains("@Value(\"${app.ai.base-url:https://api.openai.com/v1/chat/completions}\")"));
            assertTrue(source.contains("@Value(\"${app.ai.model:gpt-4o-mini}\")"));
            assertTrue(source.contains("@Value(\"${app.ai.timeout-ms:20000}\")"));
        }

        int newBuilderCount = serviceSources.values().stream()
                .mapToInt(source -> count(source, "HttpClient.newBuilder()"))
                .sum();
        int newHttpClientCount = serviceSources.values().stream()
                .mapToInt(source -> count(source, "HttpClient.newHttpClient()"))
                .sum();

        assertEquals(2, newBuilderCount);
        assertEquals(0, newHttpClientCount);
    }

    @Test
    void promptSanitizingAndJsonExtractionRemainServiceLocal() throws IOException {
        Map<String, String> serviceSources = aiServiceSources();

        for (String source : serviceSources.values()) {
            assertTrue(source.contains("private String sanitizeForPrompt(String value)"));
            assertTrue(source.contains("private String extractJson(String content)"));
            assertTrue(source.contains("private static final int FIELD_MAX_LEN = 80"));
        }
    }

    private Map<String, String> aiServiceSources() throws IOException {
        return Map.of(
                "V4ScheduleAiAnalysisService", source("src/main/java/com/paike/scheduler/service/V4ScheduleAiAnalysisService.java"),
                "V5RepairExplanationService", source("src/main/java/com/paike/scheduler/service/V5RepairExplanationService.java")
        );
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

    private int count(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private record SourceFile(String path, String content) {
    }
}

