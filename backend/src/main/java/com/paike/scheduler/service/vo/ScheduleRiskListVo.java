package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduleRiskListVo {

    private Long planId;

    private Integer riskCount;

    private Integer highRiskCount;

    private Integer mediumRiskCount;

    private Integer lowRiskCount;

    private Integer unresolvedCount;

    private List<ScheduleRiskIssueVo> risks;
}
