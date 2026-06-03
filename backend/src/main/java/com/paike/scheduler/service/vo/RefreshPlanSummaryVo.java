package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 方案质量分析刷新结果（M-14 阶段3：替换 ScheduleAnalysisController.refreshPlanSummary 原先
 * Map 弱类型返回）。
 *
 * 字段与历史 JSON 完全一致（3 字段，按原 LinkedHashMap 插入序）：planId / refreshed / message。
 * 该端点为活端点（前端 ScheduleAnalysisDetail.vue「刷新分析」按钮调用，但仅 await 后刷新数据、
 * 不读返回体字段）。三个字段均无条件 put，普通 POJO 即可，不需 NON_NULL。
 *
 * 前端 v4ScheduleAnalysisApi.refreshScheduleAnalysisSummary 声明 {planId, refreshed, message}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshPlanSummaryVo {

    private Long planId;

    private Boolean refreshed;

    private String message;
}
