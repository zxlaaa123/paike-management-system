package com.paike.scheduler.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class V5RegressionTestVo {
    private Long id;
    private String testSuite;
    private String testCase;
    private String testStage;
    private String status;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime executedAt;
}

