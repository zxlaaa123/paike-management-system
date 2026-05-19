package com.paike.scheduler.service.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class V5SimulationRoomUtilizationChangeVo {
    private Long classroomId;
    private String classroomName;
    private Integer baselineUsedPeriods;
    private Integer simulationUsedPeriods;
    private Integer deltaPeriods;
    private BigDecimal baselineUtilizationRate;
    private BigDecimal simulationUtilizationRate;
    private BigDecimal utilizationDelta;
}
