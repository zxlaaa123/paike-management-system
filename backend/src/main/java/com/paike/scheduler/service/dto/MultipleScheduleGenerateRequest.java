package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MultipleScheduleGenerateRequest {

    @Positive
    private Long semesterId;

    @Size(max = 8)
    private List<String> strategyTypes;

    private Boolean overwriteDraft;
}
