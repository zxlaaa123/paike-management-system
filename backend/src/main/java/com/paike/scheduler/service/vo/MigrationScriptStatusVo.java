package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MigrationScriptStatusVo {

    private String scriptName;
    private String resourcePath;
    private Integer configuredOrder;
    private Boolean configured;
    private Boolean existsOnClasspath;
    private String status;
    private String riskLevel;
    private String idempotentHint;
}
