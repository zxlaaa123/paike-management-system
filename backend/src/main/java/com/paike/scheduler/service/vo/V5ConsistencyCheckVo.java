package com.paike.scheduler.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class V5ConsistencyCheckVo {
    private Long id;
    private Long semesterId;
    private Long planId;
    private String checkType;
    private String status;
    private Integer issueCount;
    private Integer blockingIssueCount;
    private String resultSummary;
    private LocalDateTime checkedAt;
}

