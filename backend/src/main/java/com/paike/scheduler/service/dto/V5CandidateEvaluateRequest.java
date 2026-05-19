package com.paike.scheduler.service.dto;

import lombok.Data;

import java.util.List;

@Data
public class V5CandidateEvaluateRequest {
    private Long planId;
    private Long planItemId;
    private Integer candidateWeekday;
    private Integer candidateStartPeriod;
    private Integer candidateEndPeriod;
    private Long candidateClassroomId;
    private List<Long> scopePlanItemIds;
    private Boolean simulationOnly;
    private Long sourcePlanId;
}

