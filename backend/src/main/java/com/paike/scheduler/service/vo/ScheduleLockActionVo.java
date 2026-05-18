package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleLockActionVo {

    private Boolean locked;

    private Boolean unlocked;

    private Long lockId;

    private Long planId;

    private Long planItemId;

    private Long scheduleId;

    private String message;
}
