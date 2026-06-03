package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险诊断刷新结果（M-14 阶段3：替换 ScheduleRiskController.refreshPlanRisks 原先 Map 弱类型返回）。
 *
 * 字段与历史 JSON 完全一致（3 字段，按原 LinkedHashMap 插入序）：planId / riskCount / message。
 * riskCount 来自 ScheduleRiskListVo.getRiskCount()（Integer，可能为 null）。
 * 该端点为活端点（前端 ScheduleRiskCenter.vue「刷新风险」按钮调用，但仅 await 后刷新数据、
 * 不读返回体字段）。普通 POJO，不需 NON_NULL。
 *
 * 前端 v4ScheduleAnalysisApi.refreshScheduleRiskList 声明 {planId, riskCount, message}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshPlanRisksVo {

    private Long planId;

    private Integer riskCount;

    private String message;
}
