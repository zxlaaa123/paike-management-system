package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class V5RepairSuggestionGenerateRequest {
    private Boolean includeUnavailable;

    @Min(1)
    @Max(1000)
    private Integer candidateLimit;
}
