package com.paike.scheduler.service.dto;

import lombok.Data;

@Data
public class V5RepairSuggestionGenerateRequest {
    private Boolean includeUnavailable;
    private Integer candidateLimit;
}

