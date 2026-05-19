package com.paike.scheduler.service.dto;

import lombok.Data;

@Data
public class V5CandidatePositionGenerateRequest {
    private Long scheduleId;
    private Long planItemId;
    private Boolean includeUnavailable;
    private Integer limit;
}

