package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class V5RepairTaskCancelRequest {

    @Size(max = 255)
    private String reason;
}
