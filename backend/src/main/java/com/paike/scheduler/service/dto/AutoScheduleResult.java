package com.paike.scheduler.service.dto;

import lombok.Data;

@Data
public class AutoScheduleResult {
    private Long batchId;
    private String batchNo;
    private int totalTaskCount;
    private int successTaskCount;
    private int failedTaskCount;
    private int generatedScheduleCount;
    private String status;
    private String message;
}
