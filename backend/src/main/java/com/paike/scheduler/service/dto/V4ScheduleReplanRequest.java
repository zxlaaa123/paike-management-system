package com.paike.scheduler.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class V4ScheduleReplanRequest {

    private String newPlanName;

    private Boolean keepLocked;

    private String strategyCode;

    private Boolean forceGenerate;
}
