package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("schedule_optimization_compare")
public class ScheduleOptimizationCompare {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private Long repairTaskId;
    private Long baselinePlanId;
    private Long optimizedPlanId;
    private BigDecimal baselineTotalScore;
    private BigDecimal optimizedTotalScore;
    private BigDecimal scoreDelta;
    private Integer baselineRiskCount;
    private Integer optimizedRiskCount;
    private Integer riskDelta;
    private Integer baselineUnscheduledCount;
    private Integer optimizedUnscheduledCount;
    private Integer unscheduledDelta;
    private Integer baselineConflictCount;
    private Integer optimizedConflictCount;
    private Integer conflictDelta;
    private String compareSummary;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

