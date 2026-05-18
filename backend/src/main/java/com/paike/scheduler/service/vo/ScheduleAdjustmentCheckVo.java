package com.paike.scheduler.service.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ScheduleAdjustmentCheckVo {

    private String targetType;

    private Long planId;

    private Long planItemId;

    private Long scheduleId;

    private String courseName;

    private String teacherName;

    private String className;

    private Long currentRoomId;

    private String currentRoomName;

    private Integer currentWeekDay;

    private Integer currentPeriodStart;

    private Integer currentPeriodEnd;

    private String currentTimeLabel;

    private Long newRoomId;

    private String newRoomName;

    private Integer newWeekDay;

    private Integer newPeriodStart;

    private Integer newPeriodEnd;

    private String newTimeLabel;

    private Boolean hasConflict;

    private Integer issueCount;

    private Integer blockingIssueCount;

    private Boolean canApply;

    private List<ScheduleAdjustmentIssueVo> issues = new ArrayList<>();
}
