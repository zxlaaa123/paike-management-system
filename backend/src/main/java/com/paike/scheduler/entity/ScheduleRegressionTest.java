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
@TableName("schedule_regression_test")
public class ScheduleRegressionTest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private Long planId;
    private Long sourcePlanId;
    private String testSuite;
    private String testCase;
    private String testStage;
    private String status;
    private Long durationMs;
    private String executedBy;
    private String buildVersion;
    private String errorMessage;
    private String extraJson;
    private LocalDateTime executedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

