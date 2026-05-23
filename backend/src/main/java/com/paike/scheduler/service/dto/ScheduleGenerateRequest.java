package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ScheduleGenerateRequest {

    @Positive
    private Long semesterId;

    @Size(max = 50)
    private String strategyType;

    @Size(max = 100)
    private String planName;

    private Boolean overwriteDraft;
}
