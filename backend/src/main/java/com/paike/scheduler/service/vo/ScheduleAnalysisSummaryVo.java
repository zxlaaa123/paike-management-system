package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ScheduleAnalysisSummaryVo {

    private Long planId;
    private String planName;
    private Long termId;
    private String termName;
    private String strategyCode;
    private String planStatus;
    private Boolean isCurrent;

    private BigDecimal totalScore;
    private Integer scheduledCount;
    private Integer unscheduledCount;
    private Integer conflictCount;

    private Integer teacherCount;
    private Integer classCount;
    private Integer roomCount;
    private Integer courseCount;

    private BigDecimal teacherAverageHours;
    private Integer teacherMaxHours;
    private Integer teacherMinHours;

    private BigDecimal roomUtilizationRate;
    private BigDecimal classAverageDailyLessons;
    private Integer highRiskCount;
    private Integer mediumRiskCount;
    private Integer lowRiskCount;

    private String qualityLevel;
    private String qualitySummary;
    private List<String> suggestions;

    private LocalDateTime createdAt;
    private LocalDateTime appliedAt;
}
