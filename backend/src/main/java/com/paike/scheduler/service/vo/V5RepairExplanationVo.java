package com.paike.scheduler.service.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class V5RepairExplanationVo {
    /** 修复任务 ID */
    private Long taskId;
    /** 试算方案 ID */
    private Long planId;
    /** 是否为远程 AI 输出（false 表示走 mock/本地模板） */
    private Boolean remote;
    /** 本次解释生成时间 */
    private LocalDateTime generatedAt;

    /** 总体修复评价 */
    private String overallEvaluation;
    /** 推荐采纳的修复建议及原因 */
    private String recommendationReason;
    /** 改善的指标列表 */
    private List<String> improvedMetrics;
    /** 仍然存在的问题 */
    private List<String> remainingIssues;
    /** 是否建议应用试算方案 + 理由 */
    private String applyAdvice;
    /** 是否建议应用 */
    private Boolean recommendApply;
    /** 答辩展示文字 */
    private String defenseSummary;
    /** 通用免责声明 */
    private String disclaimer;
}
