package com.paike.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 远程 AI（OpenAI 兼容 chat completion）配置。
 *
 * 集中承载原先分散注入在 V4ScheduleAiAnalysisService / V5RepairExplanationService
 * 的 app.ai.* 配置（M-27 收敛）。默认值与历史 @Value 默认值保持一致。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiChatProperties {

    /** API Key；为空表示未启用远程 AI，调用方回退本地兜底。 */
    private String apiKey = "";

    /** chat completion 端点。 */
    private String baseUrl = "https://api.openai.com/v1/chat/completions";

    /** 模型名。 */
    private String model = "gpt-4o-mini";

    /** 请求超时（毫秒）；实际发送时不低于 5000。 */
    private long timeoutMs = 20000;
}
