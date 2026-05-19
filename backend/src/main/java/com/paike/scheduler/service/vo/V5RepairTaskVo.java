package com.paike.scheduler.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class V5RepairTaskVo {
    private Long id;
    private Long semesterId;
    private Long planId;
    private String title;
    private String taskCode;
    private String taskType;
    private String status;
    private Long resultPlanId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
