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
@TableName("schedule_report")
public class ScheduleReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private String reportType;

    private String format;

    private String status;

    private Integer includeCharts;

    private Integer includeRisks;

    private Integer includeSuggestions;

    private String filePath;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

