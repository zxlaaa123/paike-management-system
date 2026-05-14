package com.paike.scheduler.service.dto;

import lombok.Data;

import java.util.List;

@Data
public class MultipleScheduleGenerateRequest {

    private Long semesterId;

    private List<String> strategyTypes;

    private Boolean overwriteDraft;
}
