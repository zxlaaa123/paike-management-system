package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MigrationStatusOverviewVo {

    private String migrationTool;
    private Integer totalScriptCount;
    private Integer configuredScriptCount;
    private Integer missingScriptCount;
    private Integer unconfiguredScriptCount;
    private List<MigrationScriptStatusVo> scripts;
    private List<MigrationInitializerStatusVo> initializers;
}
