package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduleAiAnalysisVo {

    private Long planId;

    private String analysisType;

    private String analysisText;

    private List<String> suggestions;
}

