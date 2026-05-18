package com.paike.scheduler.service.vo;

import lombok.Data;

@Data
public class ScheduleAdjustmentApplyVo {

    private Boolean saved;

    private Boolean requiresConfirmation;

    private Boolean syncFormalSchedule;

    private Boolean syncPlanItem;

    private String message;

    private Long planId;

    private Long planItemId;

    private Long scheduleId;

    private ScheduleAdjustmentCheckVo checkResult;
}
