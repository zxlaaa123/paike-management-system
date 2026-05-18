package com.paike.scheduler.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class V4ScheduleReportGenerateRequest {

    private String reportType;

    private String format;

    private Boolean includeCharts;

    private Boolean includeRisks;

    private Boolean includeSuggestions;
}

