package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ScheduleCurrentSourceVo {

    private Long termId;

    private String termName;

    private Long sourcePlanId;

    private String sourcePlanName;

    private String strategyCode;

    private BigDecimal totalScore;

    private LocalDateTime appliedAt;

    private Boolean hasManualAdjustments;

    private Integer manualAdjustmentCount;
}
