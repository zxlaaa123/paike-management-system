package com.paike.scheduler.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class V4ScheduleAiAnalysisRequest {

    private String analysisType;

    private Boolean includeRisks;

    private Boolean includeSuggestions;
}

