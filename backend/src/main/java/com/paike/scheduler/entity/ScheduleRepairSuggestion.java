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
@TableName("schedule_repair_suggestion")
public class ScheduleRepairSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private Long planId;
    private Long repairTaskId;
    private Long sourcePlanId;
    private Long sourceScheduleId;
    private Long sourcePlanItemId;
    private String suggestionCode;
    private String suggestionType;
    private String status;
    private String priorityLevel;
    private BigDecimal expectedScoreDelta;
    private Integer expectedRiskDelta;
    private Integer expectedUnscheduledDelta;
    private String reasonSummary;
    private String detailJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

