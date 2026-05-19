package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class V5ConsistencyCheckRecordRequest {
    @NotNull
    private Long semesterId;
    private Long planId;
    private Long sourcePlanId;
    private Long scheduleId;
    @NotBlank
    private String checkType;
    private String checkScope;
    @NotBlank
    private String status;
    private Integer issueCount;
    private Integer blockingIssueCount;
    private String resultSummary;
    private String detailJson;
}

