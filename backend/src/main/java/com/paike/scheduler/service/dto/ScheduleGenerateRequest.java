package com.paike.scheduler.service.dto;

import lombok.Data;

@Data
public class ScheduleGenerateRequest {

    private Long semesterId;

    private String strategyType;

    private String planName;

    private Boolean overwriteDraft;
}
