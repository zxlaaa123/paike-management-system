package com.paike.scheduler.service.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class V5SimulationCompareVo {
    private Long baselineSemesterId;
    private Long simulationSemesterId;
    private Long baselinePlanId;
    private Long simulationPlanId;
    private Long baselineSourceScheduleId;
    private String baselinePlanName;
    private String simulationPlanName;
    private BigDecimal baselineScore;
    private BigDecimal simulationScore;
    private BigDecimal scoreDelta;
    private Integer baselineScheduledCount;
    private Integer simulationScheduledCount;
    private Integer scheduledDelta;
    private Integer baselineUnscheduledCount;
    private Integer simulationUnscheduledCount;
    private Integer unscheduledDelta;
    private Integer baselineRiskCount;
    private Integer simulationRiskCount;
    private Integer riskDelta;
    private Integer baselineHighRiskCount;
    private Integer simulationHighRiskCount;
    private Integer highRiskDelta;
    private Integer baselineMediumRiskCount;
    private Integer simulationMediumRiskCount;
    private Integer mediumRiskDelta;
    private Integer baselineLowRiskCount;
    private Integer simulationLowRiskCount;
    private Integer lowRiskDelta;
    private Integer baselineConflictCount;
    private Integer simulationConflictCount;
    private Integer conflictDelta;
    private Integer courseChangeCount;
    private Boolean lockedCoursesPreserved;
    private List<String> changedLockedCourseNames;
    private Boolean hasNewHardConflicts;
    private Integer newHardConflictCount;
    private Boolean recommended;
    private String recommendationMessage;
    private List<ScheduleRiskIssueVo> newRisks;
    private List<ScheduleRiskIssueVo> resolvedRisks;
    private List<V5SimulationLoadChangeVo> teacherLoadChanges;
    private List<V5SimulationLoadChangeVo> classLoadChanges;
    private List<V5SimulationRoomUtilizationChangeVo> roomUtilizationChanges;
    private List<V5SimulationItemChangeVo> changedItems;
    private String summary;
}
