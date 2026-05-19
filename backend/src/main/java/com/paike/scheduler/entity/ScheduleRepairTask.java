package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("schedule_repair_task")
public class ScheduleRepairTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private Long planId;
    private Long sourcePlanId;
    private Long sourceScheduleId;
    private String taskCode;
    private String taskType;
    private String status;
    private String triggerSource;
    private String riskTypes;
    private Integer targetItemCount;
    private Integer lockedItemCount;
    private Integer processedItemCount;
    private Integer successItemCount;
    private Integer failureItemCount;
    private Long resultPlanId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

