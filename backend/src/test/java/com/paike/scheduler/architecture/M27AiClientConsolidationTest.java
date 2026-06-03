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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-27 收敛后回归：远程 AI 的 HttpClient、app.ai.* 配置、JSON 提取与 prompt 清洗
 * 已统一到 RemoteAiChatClient + AiChatProperties；两个业务服务不再各自重复。
 *
 * 取代旧的 M27AiHttpClientDuplicationInvestigationTest（其锁定的是收敛前的重复现状）。
 */
class M27AiClientConsolidationTest {

    @Test
    void httpClientUsageIsConsolidatedIntoSingleClient() throws IOException {
        List<SourceFile> httpClientUsers = sourceFiles().stream()
                .filter(source -> source.content().contains("java.net.http.HttpClient"))
                .toList();

        assertEquals(1, httpClientUsers.size(), "远程 AI 的 HttpClient 用法应只剩共享客户端一处");
        assertTrue(httpClientUsers.get(0).path().endsWith("RemoteAiChatClient.java"));

        int newBuilderCount = sourceFiles().stream()
                .mapToInt(source -> count(source.content(), "HttpClient.newBuilder()"))
                .sum();
        int newHttpClientCount = sourceFiles().stream()
                .mapToInt(source -> count(source.content(), "HttpClient.newHttpClient()"))
                .sum();

        assertEquals(1, newBuilderCount);
        assertEquals(0, newHttpClientCount);
    }

    @Test
    void aiConfigurationIsCentralizedInProperties() throws IOException {
        String properties = source("src/main/java/com/paike/scheduler/config/AiChatProperties.java");
        assertTrue(properties.contains("@ConfigurationProperties(prefix = \"app.ai\")"));
        assertTrue(properties.contains("private String apiKey"));
        assertTrue(properties.contains("private String baseUrl"));
        assertTrue(properties.contains("private String model"));
        assertTrue(properties.contains("private long timeoutMs"));

        for (String svc : aiServiceSources().values()) {
            assertFalse(svc.contains("@Value(\"${app.ai"), "业务服务不应再直接注入 app.ai.* 配置");
            assertFalse(svc.contains("private static final HttpClient HTTP_CLIENT"), "业务服务不应再各自持有 HttpClient");
            assertTrue(svc.contains("RemoteAiChatClient"), "业务服务应依赖共享客户端");
        }
    }

    @Test
    void promptSanitizingAndJsonExtractionAreSharedInClient() throws IOException {
        String client = source("src/main/java/com/paike/scheduler/service/RemoteAiChatClient.java");
        assertTrue(client.contains("public String sanitizeForPrompt(String value)"));
        assertTrue(client.contains("public String extractJson(String content)"));
        assertTrue(client.contains("public String chat(String systemPrompt, String userPrompt)"));

        for (String svc : aiServiceSources().values()) {
            assertFalse(svc.contains("private String sanitizeForPrompt(String value)"), "prompt 清洗应收敛到共享客户端");
            assertFalse(svc.contains("private String extractJson(String content)"), "JSON 提取应收敛到共享客户端");
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
