package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class V4ScheduleReportGenerateRequest {

    @Size(max = 50)
    private String reportType;

    @Size(max = 20)
    private String format;

    private Boolean includeCharts;

    private Boolean includeRisks;

    private Boolean includeSuggestions;
}
