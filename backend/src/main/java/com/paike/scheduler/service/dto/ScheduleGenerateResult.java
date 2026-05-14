package com.paike.scheduler.service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScheduleGenerateResult {

    private Long planId;

    private String planName;

    private String strategyType;

    private BigDecimal totalScore;

    private Integer scheduledCount;

    private Integer unscheduledCount;

    private Integer conflictCount;
}
