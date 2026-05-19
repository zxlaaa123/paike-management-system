package com.paike.scheduler.service.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class V5SimulationCompareVo {
    private Long baselinePlanId;
    private Long simulationPlanId;
    private BigDecimal baselineScore;
    private BigDecimal simulationScore;
    private BigDecimal scoreDelta;
    private Integer baselineRiskCount;
    private Integer simulationRiskCount;
    private Integer riskDelta;
    private Integer baselineConflictCount;
    private Integer simulationConflictCount;
    private Integer conflictDelta;
    private List<V5SimulationItemChangeVo> changedItems;
    private String summary;
}
