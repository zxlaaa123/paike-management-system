package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleLockRequest {

    @NotBlank
    @Size(max = 20)
    private String targetType;

    @Positive
    private Long planId;

    @Positive
    private Long planItemId;

    @Positive
    private Long scheduleId;

    @Size(max = 255)
    private String lockReason;
}
