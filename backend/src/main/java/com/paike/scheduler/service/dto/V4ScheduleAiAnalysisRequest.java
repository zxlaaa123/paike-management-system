package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class V4ScheduleAiAnalysisRequest {

    @Size(max = 32)
    private String analysisType;

    private Boolean includeRisks;

    private Boolean includeSuggestions;
}
