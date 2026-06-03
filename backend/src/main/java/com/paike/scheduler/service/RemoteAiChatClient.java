package com.paike.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.config.AiChatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 远程 AI chat completion 共享客户端（M-27 收敛）。
 *
 * 只负责与业务无关的通用部分：HTTP envelope、鉴权、超时、状态码校验、原始 content 提取，
 * 以及通用的 prompt 字段清洗与 JSON 子串提取。
 * 各业务服务（V4ScheduleAiAnalysisService / V5RepairExplanationService）继续保留
 * 自己的 prompt 组装、业务 JSON schema 解析和本地兜底，避免把业务 schema 混进通用客户端。
 */
@Component
@RequiredArgsConstructor
public class RemoteAiChatClient {

    /**
     * HttpClient 内部持有 Selector + ExecutorService 线程池，每次 newHttpClient() 都会创建一组守护线程，
     * 频繁调用会导致线程数缓慢累积（JDK 17 上不会被显式关闭）。改为共享单例。
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 请求超时下限（毫秒）。 */
    private static final long MIN_TIMEOUT_MS = 5000;

    /** prompt 中的用户字段最大长度，超出截断，避免长输入 + prompt injection 同时放大。 */
    private static final int FIELD_MAX_LEN = 80;

    private final AiChatProperties properties;
    private final ObjectMapper objectMapper;

    /** 远程 AI 是否可用（凭据与端点齐备）。 */
    public boolean isEnabled() {
        return hasText(properties.getApiKey())
                && hasText(properties.getBaseUrl())
                && hasText(properties.getModel());
    }

    /**
     * 发送 chat completion 请求，返回模型原始 content 文本。
     * 已做状态码与空内容校验；失败抛 BusinessException（由调用方捕获后回退本地兜底）。
     */
    public String chat(String systemPrompt, String userPrompt) throws Exception {
        JsonNode requestBody = objectMapper.createObjectNode()
                .put("model", properties.getModel())
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", systemPrompt))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", userPrompt)));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl()))
                .timeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), MIN_TIMEOUT_MS)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("AI 调用失败，状态码: " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new BusinessException("AI 返回内容为空");
        }
        return content;
    }

    /**
     * 从模型 content 中提取 JSON 对象子串（容忍 content 前后包裹解释文字或代码块围栏）。
     */
    public String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        throw new BusinessException("AI 返回格式异常");
    }

    /**
     * 把任意用户/业务字段插入 prompt 之前必须先过这里：
     * - 去掉换行/制表符，防止伪造新的指令段落
     * - 去掉反引号、围栏，避免 break out 代码块
     * - 截断到 FIELD_MAX_LEN，防止超长输入挤掉系统指令
     */
    public String sanitizeForPrompt(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String cleaned = value
                .replaceAll("[\\r\\n\\t]+", " ")
                .replace("```", "ˋˋˋ")
                .replace("`", "ˋ")
                .trim();
        if (cleaned.length() > FIELD_MAX_LEN) {
            cleaned = cleaned.substring(0, FIELD_MAX_LEN) + "…";
        }
        return cleaned;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
