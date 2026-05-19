package com.paike.scheduler.service.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class V5RepairSuggestionVo {
    private Long id;
    private Long repairTaskId;
    private String suggestionCode;
    private String suggestionType;
    private String recommendationLevel;
    private String status;
    private Long riskItemId;
    private String riskType;
    private Long sourcePlanItemId;
    private Integer sourceWeekday;
    private Integer sourceStartPeriod;
    private Integer sourceEndPeriod;
    private Long sourceClassroomId;
    private String sourceClassroomName;
    private Integer targetWeekday;
    private Integer targetStartPeriod;
    private Integer targetEndPeriod;
    private Long targetClassroomId;
    private String targetClassroomName;
    private Boolean resolvesOriginalRisk;
    private Boolean introducesNewRisk;
    private List<Long> affectedItems;
    private BigDecimal expectedScoreDelta;
    private String reasonSummary;
    private String description;
    private LocalDateTime createdAt;
}

