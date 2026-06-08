package com.paike.scheduler.service.vo;

import lombok.Data;

@Data
public class PerformanceSummaryVo {
    private String operationType;
    private Long totalCount;
    private Long successCount;
    private Long failureCount;
    private Long avgDurationMs;
    private Long maxDurationMs;
}

