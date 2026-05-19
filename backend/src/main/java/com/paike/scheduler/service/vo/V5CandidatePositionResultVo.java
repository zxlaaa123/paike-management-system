package com.paike.scheduler.service.vo;

import lombok.Data;

import java.util.List;

@Data
public class V5CandidatePositionResultVo {
    private Long semesterId;
    private Long planId;
    private Long planItemId;
    private Long scheduleId;
    private Integer sourceWeekday;
    private Integer sourceStartPeriod;
    private Integer sourceEndPeriod;
    private Long sourceClassroomId;
    private String sourceClassroomName;
    private Integer totalCount;
    private Integer availableCount;
    private List<V5CandidatePositionVo> candidates;
}

