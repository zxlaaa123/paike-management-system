package com.paike.scheduler.service.dto;

import lombok.Data;

import java.util.List;

@Data
public class V5LocalReplanRequest {
    private String newPlanName;
    private List<Long> classIds;
    private List<Long> teacherIds;
    private List<Long> classroomIds;
    private List<Integer> weekdays;
    private List<Integer> periodNos;
    private List<Long> riskItemIds;
    private List<Long> selectedPlanItemIds;
    private Integer candidateLimit;
}
