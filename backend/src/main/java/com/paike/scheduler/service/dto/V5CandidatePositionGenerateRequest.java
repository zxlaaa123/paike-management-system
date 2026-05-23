package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class V5CandidatePositionGenerateRequest {
    @Positive
    private Long scheduleId;

    @Positive
    private Long planItemId;

    private Boolean includeUnavailable;

    @Min(1)
    @Max(1000)
    private Integer limit;
}
