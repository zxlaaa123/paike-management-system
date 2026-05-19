package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class V5RepairTaskCreateRequest {
    @NotNull
    private Long semesterId;
    @NotNull
    private Long planId;
    private Long sourcePlanId;
    private Long sourceScheduleId;
    @NotBlank
    private String taskCode;
    @NotBlank
    private String taskType;
    private String triggerSource;
    private String riskTypes;
}

