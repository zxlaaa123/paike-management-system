package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 方案对比结果（M-14 阶段2：替换 ScheduleCompareService.compare 原先的 Map 弱类型返回）。
 * 字段与历史 JSON 完全一致（4 字段）：semesterId / plans / bestPlanId / summary。
 * 前端 CompareResult 类型逐字段对齐。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompareResultVo {

    private Long semesterId;

    private List<ComparePlanVo> plans;

    private Long bestPlanId;

    private String summary;
}
