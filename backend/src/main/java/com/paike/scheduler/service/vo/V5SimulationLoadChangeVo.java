package com.paike.scheduler.service.vo;

import lombok.Data;

@Data
public class V5SimulationLoadChangeVo {
    private Long entityId;
    private String entityName;
    private Integer baselineLoad;
    private Integer simulationLoad;
    private Integer delta;
}
