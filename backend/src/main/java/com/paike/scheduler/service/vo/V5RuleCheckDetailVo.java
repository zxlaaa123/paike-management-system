package com.paike.scheduler.service.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class V5RuleCheckDetailVo {
    private String ruleCode;
    private String ruleName;
    private String ruleType;
    private Boolean passed;
    private Boolean blocking;
    private BigDecimal scoreDelta;
    private String message;
}

