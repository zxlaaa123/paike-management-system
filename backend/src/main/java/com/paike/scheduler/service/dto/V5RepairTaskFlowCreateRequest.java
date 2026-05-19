package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class V5RepairTaskFlowCreateRequest {
    @NotNull
    private Long semesterId;
    private Long planId;
    private Long sourceScheduleId;
    private Long sourcePlanId;
    @NotBlank
    private String taskType;
    private String title;
    private String triggerSource;
    private List<String> riskTypes;
    private List<Long> riskItemIds;
    private List<Long> scopePlanItemIds;
}

