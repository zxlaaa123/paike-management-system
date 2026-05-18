package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ScheduleScoreDetailExplainVo {

    private Long planId;
    private String planName;
    private String strategyCode;
    private BigDecimal totalScore;
    private String calculationSource;
    private List<ScheduleScoreItemVo> scoreItems;
}
