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
@TableName("schedule_generate_log")
public class ScheduleGenerateLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long semesterId;

    private Long teachingTaskId;

    private String logLevel;

    private String logType;

    private String message;

    private Integer stepNo;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
