package com.paike.scheduler.service.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class V5RuleMetaVo {
    private String ruleCode;
    private String ruleName;
    private String ruleType;
    private String description;
    private Boolean enabled;
    private BigDecimal weight;
    private Boolean nonDisableable;
}

