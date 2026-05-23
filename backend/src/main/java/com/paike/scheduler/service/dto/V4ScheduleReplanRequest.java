package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class V4ScheduleReplanRequest {

    @Size(max = 100)
    private String newPlanName;

    private Boolean keepLocked;

    @Size(max = 50)
    private String strategyCode;

    private Boolean forceGenerate;
}
