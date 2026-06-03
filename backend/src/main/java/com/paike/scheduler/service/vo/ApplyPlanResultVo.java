package com.paike.scheduler.service.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 方案应用/回滚结果（M-14 阶段2：替换 SchedulePlanService 的 applyPlan / applySimulationPlan /
 * rollbackPlan 原先的 Map 弱类型返回，并经 V5SimulationService.apply 透传）。
 *
 * 字段与历史 JSON 完全一致：planId / semesterId / appliedCount / appliedAt（4 字段）。
 * applyPlanInternal 与 apply/applySimulation/rollback 委派分支不带 message；唯独
 * rollbackPlan 的「目标方案已是当前应用方案」短路分支多一个 message。
 * 因此 message 标注 {@code @JsonInclude(NON_NULL)}：为 null 时省略，JSON 仍是 4 字段，
 * 仅 rollback 短路分支序列化出第 5 个 message 字段——与替换前逐字节一致。
 *
 * 前端 applySchedulePlan / rollbackSchedulePlan / applySimulation 类型均为
 * {planId, semesterId, appliedCount, appliedAt}，不读 message。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyPlanResultVo {

    private Long planId;

    private Long semesterId;

    private Integer appliedCount;

    private LocalDateTime appliedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;

    /** 不带 message 的应用结果（applyPlanInternal / apply / rollback 委派分支）。 */
    public ApplyPlanResultVo(Long planId, Long semesterId, Integer appliedCount, LocalDateTime appliedAt) {
        this(planId, semesterId, appliedCount, appliedAt, null);
    }
}
