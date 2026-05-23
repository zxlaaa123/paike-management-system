package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AutoScheduleRequest {
    @NotNull(message = "semesterId 不能为空")
    @Positive(message = "semesterId 必须大于 0")
    private Long semesterId;

    @Size(max = 5000)
    private List<Long> taskIds;
    private boolean clearOldAutoSchedule = true;
    private boolean clearAllSchedule = false;
}
