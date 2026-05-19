package com.paike.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.service.dto.V4ScheduleAiAnalysisRequest;
import com.paike.scheduler.service.vo.ScheduleAiAnalysisVo;
import com.paike.scheduler.service.vo.ScheduleAnalysisSummaryVo;
import com.paike.scheduler.service.vo.ScheduleRiskIssueVo;
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class V4ScheduleAiAnalysisService {

    private static final List<String> SUPPORTED_ANALYSIS_TYPES = List.of("SUMMARY", "RISK", "OPTIMIZATION", "DEFENSE", "REPORT_SUMMARY");

    @Value("${app.ai.api-key:}")
    private String aiApiKey;

    @Value("${app.ai.base-url:https://api.openai.com/v1/chat/completions}")
    private String aiBaseUrl;

    @Value("${app.ai.model:gpt-4o-mini}")
    private String aiModel;

    @Value("${app.ai.timeout-ms:20000}")
    private long aiTimeoutMs;

    private final V4ScheduleAnalysisService scheduleAnalysisService;
    private final V4ScheduleRiskService scheduleRiskService;
    private final ObjectMapper objectMapper;

    public ScheduleAiAnalysisVo generateAnalysis(Long planId, V4ScheduleAiAnalysisRequest request) {
        String analysisType = normalizeAnalysisType(request == null ? null : request.getAnalysisType());
        boolean includeRisks = request == null || request.getIncludeRisks() == null || request.getIncludeRisks();
        boolean includeSuggestions = request == null || request.getIncludeSuggestions() == null || request.getIncludeSuggestions();

        ScheduleAnalysisSummaryVo summary = scheduleAnalysisService.getPlanSummary(planId);
        ScheduleRiskListVo riskList = includeRisks ? scheduleRiskService.getPlanRisks(planId, null, null, false) : null;

        AiOutput output = null;
        if (isRemoteAiEnabled()) {
            try {
                output = callRemoteAi(analysisType, summary, riskList, includeSuggestions);
            } catch (Exception ex) {
                log.warn("Remote AI request failed, fallback to local analysis: {}", ex.getMessage());
            }
        }
        if (output == null) {
            output = buildLocalOutput(analysisType, summary, riskList, includeSuggestions);
        }

        ScheduleAiAnalysisVo vo = new ScheduleAiAnalysisVo();
        vo.setPlanId(planId);
        vo.setAnalysisType(analysisType);
        vo.setAnalysisText(output.analysisText);
        vo.setSuggestions(output.suggestions);
        return vo;
    }

    private AiOutput callRemoteAi(
            String analysisType,
            ScheduleAnalysisSummaryVo summary,
            ScheduleRiskListVo riskList,
            boolean includeSuggestions
    ) throws Exception {
        String prompt = buildPrompt(analysisType, summary, riskList, includeSuggestions);

        JsonNode requestBody = objectMapper.createObjectNode()
                .put("model", aiModel)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", "你是高校排课质量分析助手。只给分析与建议，不给任何自动改课动作。请严格输出 JSON：{\"analysisText\":\"...\",\"suggestions\":[\"...\"]}。"))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt)));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(aiBaseUrl))
                .timeout(Duration.ofMillis(Math.max(aiTimeoutMs, 5000)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("AI 分析调用失败，状态码: " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new BusinessException("AI 返回内容为空");
        }
        String jsonText = extractJson(content);
        JsonNode parsed = objectMapper.readTree(jsonText);
        String analysisText = parsed.path("analysisText").asText("").trim();
        List<String> suggestions = new ArrayList<>();
        if (parsed.path("suggestions").isArray()) {
            parsed.path("suggestions").forEach(node -> {
                String value = node.asText("").trim();
                if (!value.isBlank()) {
                    suggestions.add(value);
                }
            });
        }
        if (analysisText.isBlank()) {
            throw new BusinessException("AI 返回分析文本为空");
        }
        return new AiOutput(analysisText, suggestions);
    }

    private AiOutput buildLocalOutput(
            String analysisType,
            ScheduleAnalysisSummaryVo summary,
            ScheduleRiskListVo riskList,
            boolean includeSuggestions
    ) {
        String analysisText = switch (analysisType) {
            case "RISK" -> buildRiskAnalysis(summary, riskList);
            case "OPTIMIZATION" -> buildOptimizationAnalysis(summary, riskList);
            case "DEFENSE" -> buildDefenseAnalysis(summary, riskList);
            case "REPORT_SUMMARY" -> buildReportSummary(summary, riskList);
            default -> buildSummaryAnalysis(summary, riskList);
        };

        List<String> suggestions = includeSuggestions
                ? buildLocalSuggestions(summary, riskList, analysisType)
                : List.of();

        return new AiOutput(analysisText, suggestions);
    }

    private String buildSummaryAnalysis(ScheduleAnalysisSummaryVo summary, ScheduleRiskListVo riskList) {
        StringBuilder text = new StringBuilder();
        text.append("该方案当前总分为 ").append(safeDecimal(summary.getTotalScore()))
                .append("，质量等级为 ").append(safe(summary.getQualityLevel()))
                .append("。已排任务 ").append(safeInt(summary.getScheduledCount()))
                .append("，未排任务 ").append(safeInt(summary.getUnscheduledCount()))
                .append("，冲突数量 ").append(safeInt(summary.getConflictCount()))
                .append("。");
        if (riskList != null) {
            text.append("风险侧高/中/低分别为 ")
                    .append(safeInt(riskList.getHighRiskCount())).append("/")
                    .append(safeInt(riskList.getMediumRiskCount())).append("/")
                    .append(safeInt(riskList.getLowRiskCount()))
                    .append("，未解决风险 ").append(safeInt(riskList.getUnresolvedCount()))
                    .append("。");
        }
        text.append("教师平均负载 ").append(safeDecimal(summary.getTeacherAverageHours()))
                .append(" 节，教室利用率 ").append(safeDecimal(summary.getRoomUtilizationRate()))
                .append("%。");
        return text.toString();
    }

    private String buildRiskAnalysis(ScheduleAnalysisSummaryVo summary, ScheduleRiskListVo riskList) {
        if (riskList == null) {
            return "当前请求未包含风险数据，请启用“包含风险”后重新生成。";
        }
        String topRisk = riskList.getRisks() == null || riskList.getRisks().isEmpty()
                ? "暂无具体风险明细。"
                : "当前优先风险包括：" + riskList.getRisks().stream()
                .limit(3)
                .map(ScheduleRiskIssueVo::getTitle)
                .filter(Objects::nonNull)
                .reduce((a, b) -> a + "；" + b)
                .orElse("暂无具体风险明细。");
        return "该方案高风险 " + safeInt(riskList.getHighRiskCount())
                + " 项，中风险 " + safeInt(riskList.getMediumRiskCount())
                + " 项，低风险 " + safeInt(riskList.getLowRiskCount())
                + " 项，未解决风险 " + safeInt(riskList.getUnresolvedCount())
                + " 项。" + topRisk;
    }

    private String buildOptimizationAnalysis(ScheduleAnalysisSummaryVo summary, ScheduleRiskListVo riskList) {
        StringBuilder text = new StringBuilder();
        text.append("优化重点建议围绕“先硬后软”：先处理未排任务与冲突，再做负载均衡和利用率优化。")
                .append("当前未排任务 ").append(safeInt(summary.getUnscheduledCount()))
                .append("，冲突 ").append(safeInt(summary.getConflictCount()))
                .append("，教师最大负载 ").append(safeInt(summary.getTeacherMaxHours()))
                .append("，教室利用率 ").append(safeDecimal(summary.getRoomUtilizationRate())).append("%。");
        if (riskList != null && safeInt(riskList.getHighRiskCount()) > 0) {
            text.append("建议先消化高风险项，再进入局部重排。");
        }
        return text.toString();
    }

    private String buildDefenseAnalysis(ScheduleAnalysisSummaryVo summary, ScheduleRiskListVo riskList) {
        return "若用于方案答辩，可强调三点："
                + "第一，方案完成度（已排 " + safeInt(summary.getScheduledCount()) + " / 未排 " + safeInt(summary.getUnscheduledCount()) + "）；"
                + "第二，质量结果（总分 " + safeDecimal(summary.getTotalScore()) + "，等级 " + safe(summary.getQualityLevel()) + "）；"
                + "第三，风险与改进路径（冲突 " + safeInt(summary.getConflictCount())
                + (riskList == null ? "" : "，高风险 " + safeInt(riskList.getHighRiskCount()))
                + "，后续通过风险诊断和局部重排持续优化）。";
    }

    private String buildReportSummary(ScheduleAnalysisSummaryVo summary, ScheduleRiskListVo riskList) {
        return "报告摘要：方案 " + safe(summary.getPlanName())
                + " 当前总分 " + safeDecimal(summary.getTotalScore())
                + "，已排任务 " + safeInt(summary.getScheduledCount())
                + "，未排任务 " + safeInt(summary.getUnscheduledCount())
                + "，冲突 " + safeInt(summary.getConflictCount())
                + (riskList == null ? "" : "，风险高/中/低 " + safeInt(riskList.getHighRiskCount()) + "/" + safeInt(riskList.getMediumRiskCount()) + "/" + safeInt(riskList.getLowRiskCount()))
                + "。";
    }

    private List<String> buildLocalSuggestions(ScheduleAnalysisSummaryVo summary, ScheduleRiskListVo riskList, String analysisType) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        if (safeInt(summary.getUnscheduledCount()) > 0) {
            suggestions.add("优先处理未排任务，避免方案可执行性受限。");
        }
        if (safeInt(summary.getConflictCount()) > 0) {
            suggestions.add("先定位冲突来源，再进行局部调整或重排。");
        }
        if (summary.getTeacherAverageHours() != null && summary.getTeacherMaxHours() != null) {
            BigDecimal threshold = summary.getTeacherAverageHours().multiply(BigDecimal.valueOf(1.6));
            if (BigDecimal.valueOf(summary.getTeacherMaxHours()).compareTo(threshold) > 0) {
                suggestions.add("关注教师课时头部集中问题，优化教师负载均衡。");
            }
        }
        if (summary.getRoomUtilizationRate() != null && summary.getRoomUtilizationRate().compareTo(BigDecimal.valueOf(40)) < 0) {
            suggestions.add("结合图表页检查低利用率教室，优先调整容量/类型错配课程。");
        }
        if (riskList != null && safeInt(riskList.getHighRiskCount()) > 0) {
            suggestions.add("高风险项建议按“教师冲突、班级冲突、教室冲突”顺序逐一清理。");
        }
        if ("DEFENSE".equals(analysisType)) {
            suggestions.add("答辩时给出“问题-措施-预期改善”的三段式说明，便于评审快速理解。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("当前方案指标较稳定，建议持续跟踪风险变化并定期复核。");
        }
        return new ArrayList<>(suggestions).subList(0, Math.min(suggestions.size(), 6));
    }

    private String buildPrompt(
            String analysisType,
            ScheduleAnalysisSummaryVo summary,
            ScheduleRiskListVo riskList,
            boolean includeSuggestions
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请基于以下排课数据，输出 ").append(typeLabel(analysisType)).append("。\n")
                .append("要求：\n")
                .append("1) 只做分析和建议，不要给自动改课动作。\n")
                .append("2) 使用中文。\n")
                .append("3) analysisText 不超过 220 字。\n")
                .append("4) suggestions 最多 6 条，每条不超过 36 字。\n")
                .append("5) 输出严格 JSON，键名为 analysisText 和 suggestions。\n")
                .append("6) 如果 includeSuggestions=false，则 suggestions 返回空数组。\n\n")
                .append("输入数据：\n")
                .append("- 方案名: ").append(safe(summary.getPlanName())).append("\n")
                .append("- 总分: ").append(safeDecimal(summary.getTotalScore())).append("\n")
                .append("- 质量等级: ").append(safe(summary.getQualityLevel())).append("\n")
                .append("- 已排/未排/冲突: ").append(safeInt(summary.getScheduledCount())).append("/")
                .append(safeInt(summary.getUnscheduledCount())).append("/")
                .append(safeInt(summary.getConflictCount())).append("\n")
                .append("- 教师平均/最大负载: ").append(safeDecimal(summary.getTeacherAverageHours())).append("/")
                .append(safeInt(summary.getTeacherMaxHours())).append("\n")
                .append("- 教室利用率: ").append(safeDecimal(summary.getRoomUtilizationRate())).append("%\n");

        if (riskList != null) {
            prompt.append("- 风险高/中/低/未解决: ").append(safeInt(riskList.getHighRiskCount()))
                    .append("/").append(safeInt(riskList.getMediumRiskCount()))
                    .append("/").append(safeInt(riskList.getLowRiskCount()))
                    .append("/").append(safeInt(riskList.getUnresolvedCount())).append("\n");
            if (riskList.getRisks() != null && !riskList.getRisks().isEmpty()) {
                prompt.append("- 代表风险: ");
                riskList.getRisks().stream().limit(3).map(ScheduleRiskIssueVo::getTitle).filter(Objects::nonNull).forEach(title -> prompt.append("[").append(title).append("]"));
                prompt.append("\n");
            }
        }
        prompt.append("- includeSuggestions: ").append(includeSuggestions);
        return prompt.toString();
    }

    private String normalizeAnalysisType(String raw) {
        String value = raw == null || raw.isBlank() ? "SUMMARY" : raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ANALYSIS_TYPES.contains(value)) {
            throw new BusinessException("不支持的分析类型");
        }
        return value;
    }

    private boolean isRemoteAiEnabled() {
        return aiApiKey != null && !aiApiKey.isBlank() && aiBaseUrl != null && !aiBaseUrl.isBlank() && aiModel != null && !aiModel.isBlank();
    }

    private String typeLabel(String analysisType) {
        return switch (analysisType) {
            case "RISK" -> "风险分析";
            case "OPTIMIZATION" -> "优化建议";
            case "DEFENSE" -> "答辩说明";
            case "REPORT_SUMMARY" -> "报告摘要";
            default -> "总体分析";
        };
    }

    private String extractJson(String content) {
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

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String safeDecimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record AiOutput(String analysisText, List<String> suggestions) {
    }
}

