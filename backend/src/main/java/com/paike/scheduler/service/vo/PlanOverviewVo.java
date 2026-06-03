package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 方案统计总览（M-14 阶段3：替换 ScheduleStatisticsService.planOverview 原先 Map 弱类型返回）。
 *
 * 字段与历史 JSON 完全一致（17 字段，按原 LinkedHashMap 插入序）。
 *
 * 关于 best/applied 两组各 4 个字段：历史代码在 bestPlan/appliedPlan 为 null 时根本不 put 这些
 * key（条件 put），但前端 PlanOverview 把它们声明为 number|null（预期键恒存在）。本 VO 采用
 * 普通 POJO（不加 @JsonInclude(NON_NULL)）：
 * - 有最佳/应用方案时（常见路径）：与历史 Map 逐字节一致；
 * - 子字段为 null（如 bestPlanName 为 null）时：输出 null，与历史「if 块跑了就 put null」一致；
 *   （若改用 NON_NULL 会误省略该键，反而与历史不符）
 * - 无最佳/应用方案时（边缘路径）：多输出 null 键，前端 number|null 兼容、无害。
 *
 * 字段顺序：semesterId/totalPlans/draftPlans/appliedPlans/abandonedPlans -> best 块 ->
 * hasAppliedPlan -> applied 块 -> formalScheduleCount/totalUnassignedTasks/totalConflicts，
 * 照原插入序声明。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanOverviewVo {

    private Long semesterId;

    private Integer totalPlans;

    /** 来自 Stream.count()，故为 Long。 */
    private Long draftPlans;

    private Long appliedPlans;

    private Long abandonedPlans;

    private Long bestPlanId;

    private String bestPlanName;

    private BigDecimal bestPlanScore;

    private String bestPlanStrategy;

    private Boolean hasAppliedPlan;

    private Long appliedPlanId;

    private String appliedPlanName;

    private BigDecimal appliedPlanScore;

    private LocalDateTime appliedPlanAppliedAt;

    /** 来自 selectCount，故为 Long。 */
    private Long formalScheduleCount;

    private Integer totalUnassignedTasks;

    private Integer totalConflicts;
}
