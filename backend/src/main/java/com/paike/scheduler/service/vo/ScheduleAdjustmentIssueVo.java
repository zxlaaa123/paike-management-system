package com.paike.scheduler.service.vo;

import lombok.Data;

@Data
public class ScheduleAdjustmentIssueVo {

    private String issueType;

    private String issueName;

    private Boolean blocking;

    private String message;
}
