package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class V5RegressionTestRecordRequest {
    private Long semesterId;
    private Long planId;
    private Long sourcePlanId;
    @NotBlank
    private String testSuite;
    private String testCase;
    private String testStage;
    @NotBlank
    private String status;
    private Long durationMs;
    private String executedBy;
    private String buildVersion;
    private String errorMessage;
    private String extraJson;
}

