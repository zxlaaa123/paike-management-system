package com.paike.scheduler.service.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class V5RepairTaskDetailVo {
    private Long id;
    private Long semesterId;
    private Long planId;
    private Long sourcePlanId;
    private Long sourceScheduleId;
    private Long resultPlanId;
    private String taskCode;
    private String title;
    private String taskType;
    private String status;
    private String triggerSource;
    private List<String> riskTypes;
    private List<Long> riskItemIds;
    private List<Long> scopePlanItemIds;
    private Integer targetItemCount;
    private Integer lockedItemCount;
    private Integer processedItemCount;
    private Integer successItemCount;
    private Integer failureItemCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

