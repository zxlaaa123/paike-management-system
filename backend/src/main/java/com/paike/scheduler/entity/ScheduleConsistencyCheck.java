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
@TableName("schedule_consistency_check")
public class ScheduleConsistencyCheck {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private Long planId;
    private Long sourcePlanId;
    private Long scheduleId;
    private String checkType;
    private String checkScope;
    private String status;
    private Integer issueCount;
    private Integer blockingIssueCount;
    private String resultSummary;
    private String detailJson;
    private LocalDateTime checkedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

