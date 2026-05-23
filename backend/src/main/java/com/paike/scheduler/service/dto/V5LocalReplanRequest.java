package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class V5LocalReplanRequest {
    @Size(max = 100)
    private String newPlanName;
    private List<Long> classIds;
    private List<Long> teacherIds;
    private List<Long> classroomIds;
    private List<Integer> weekdays;
    private List<Integer> periodNos;
    private List<Long> riskItemIds;
    private List<Long> selectedPlanItemIds;
    @Min(1)
    @Max(2000)
    private Integer candidateLimit;
}
