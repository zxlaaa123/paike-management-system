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
@TableName("schedule_candidate_position")
public class ScheduleCandidatePosition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private Long planId;
    private Long repairTaskId;
    private Long suggestionId;
    private Long sourcePlanId;
    private Long sourceScheduleId;
    private Long planItemId;
    private Long teachingTaskId;
    private Integer candidateWeekday;
    private Integer candidateStartPeriod;
    private Integer candidateEndPeriod;
    private Long candidateClassroomId;
    private Long candidateTimeSlotId;
    private BigDecimal candidateScore;
    private Integer hardConflictCount;
    private BigDecimal softPenaltyScore;
    private Integer isRecommended;
    private Integer rankNo;
    private String reasonSummary;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

