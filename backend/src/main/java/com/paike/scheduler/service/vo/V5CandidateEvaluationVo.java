package com.paike.scheduler.service.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class V5CandidateEvaluationVo {
    private Long planId;
    private Long planItemId;
    private Integer candidateWeekday;
    private Integer candidateStartPeriod;
    private Integer candidateEndPeriod;
    private Long candidateClassroomId;
    private Boolean available;
    private Integer hardViolationCount;
    private BigDecimal softScoreDelta;
    private BigDecimal preferenceScoreDelta;
    private BigDecimal totalScoreDelta;
    private String summary;
    private List<V5RuleCheckDetailVo> details;
}

