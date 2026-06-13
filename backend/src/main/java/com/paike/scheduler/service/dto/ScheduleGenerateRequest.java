package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ScheduleGenerateRequest {

    @Positive
    private Long semesterId;

    @Size(max = 50)
    private String strategyType;

    @Size(max = 100)
    private String planName;

    private Boolean overwriteDraft;

    private Long solverSeed;

    private Long solverTimeBudgetMs;

    /** 模拟退火优化时间预算（毫秒），仅 SOLVER_V8 生效；缺省走引擎默认 10s，传 0 跳过退火。 */
    private Long solverOptimizeTimeBudgetMs;
}
