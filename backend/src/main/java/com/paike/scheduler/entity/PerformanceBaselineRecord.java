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
@TableName("performance_baseline_record")
public class PerformanceBaselineRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String operationType;
    private Long semesterId;
    private Long planId;
    private Long targetId;
    private Integer taskCount;
    private Integer scheduleCount;
    private Long durationMs;
    private Integer success;
    private String errorCode;
    private String errorMessage;
    private String extraJson;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

