package com.paike.scheduler.service.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class V5CandidatePositionVo {
    private Integer weekday;
    private Integer startPeriod;
    private Integer endPeriod;
    private Long classroomId;
    private String classroomName;
    private Boolean available;
    private Integer hardConflictCount;
    private BigDecimal softScore;
    private BigDecimal totalScore;
    private String reason;
    private List<Long> affectedItems;
}

