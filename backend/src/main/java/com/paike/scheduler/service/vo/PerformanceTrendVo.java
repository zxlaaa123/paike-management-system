package com.paike.scheduler.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PerformanceTrendVo {
    private Long id;
    private String operationType;
    private Long durationMs;
    private Long previousDurationMs;
    private Long durationDeltaMs;
    private Integer durationChangePercent;
    private Boolean slowOperation;
    private Long slowThresholdMs;
    private Integer success;
    private LocalDateTime createdAt;
}
