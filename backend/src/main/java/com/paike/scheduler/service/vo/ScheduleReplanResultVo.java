package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ScheduleReplanResultVo {

    private Long sourcePlanId;

    private String sourcePlanName;

    private Long newPlanId;

    private String newPlanName;

    private Integer lockedCount;

    private Integer replanableCount;

    private Integer scheduledCount;

    private Integer unscheduledCount;

    private Integer conflictCount;

    private BigDecimal totalScore;

    private Boolean keepLocked;

    private String strategyCode;

    private Boolean minimalMode;

    private String message;
}
