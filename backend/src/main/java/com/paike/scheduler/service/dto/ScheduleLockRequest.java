package com.paike.scheduler.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleLockRequest {

    private String targetType;

    private Long planId;

    private Long planItemId;

    private Long scheduleId;

    private String lockReason;
}
