package com.paike.scheduler.service.dto;

import lombok.Data;

import java.util.List;

@Data
public class AutoScheduleRequest {
    private List<Long> taskIds;
    private boolean clearOldAutoSchedule = true;
    private boolean clearAllSchedule = false;
}
