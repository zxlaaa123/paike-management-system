package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 方案对比单方案信息（M-14 阶段2：替换 ScheduleCompareService.buildPlanCompareInfo
 * 原先 List 中的 Map 弱类型元素）。
 * 字段与历史 JSON 完全一致（12 字段）：planId / planName / strategyType / strategyName /
 * status / totalScore / scheduledCount / unscheduledCount / conflictCount /
 * hardViolationCount / softViolationCount / generatedAt。
 *
 * totalScore 非空（无评分时回退 BigDecimal.ZERO）；其余计数与 generatedAt 可能为 null，
 * 保留 null 序列化（对齐原 LinkedHashMap）。前端 ComparePlan 类型逐字段对齐。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComparePlanVo {

    private Long planId;

    private String planName;

    private String strategyType;

    private String strategyName;

    private String status;

    private BigDecimal totalScore;

    private Integer scheduledCount;

    private Integer unscheduledCount;

    private Integer conflictCount;

    private Integer hardViolationCount;

    private Integer softViolationCount;

    private LocalDateTime generatedAt;
}
