package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ScheduleReportItemVo {

    private Long reportId;

    private Long planId;

    private String reportType;

    private String format;

    private String status;

    private String downloadUrl;

    private LocalDateTime createdAt;
}

