package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class V5RepairTaskStatusUpdateRequest {
    @NotBlank
    private String status;
    private String message;
}

