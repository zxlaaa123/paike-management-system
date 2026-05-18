package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ScheduleScoreItemVo {

    private String scoreKey;
    private String scoreName;
    private BigDecimal scoreValue;
    private BigDecimal maxScore;
    private BigDecimal weight;
    private String description;
    private Integer violationCount;
    private String detailMessage;
}
