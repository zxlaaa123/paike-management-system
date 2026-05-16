package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("schedule_adjust_log")
public class ScheduleAdjustLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long scheduleId;

    private Long semesterId;

    private Long teachingTaskId;

    private Long oldClassroomId;

    private Integer oldWeekday;

    private Integer oldStartPeriod;

    private Integer oldEndPeriod;

    private Long newClassroomId;

    private Integer newWeekday;

    private Integer newStartPeriod;

    private Integer newEndPeriod;

    private BigDecimal beforeScore;

    private BigDecimal afterScore;

    private Integer conflictFlag;

    private String adjustReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String courseName;

    @TableField(exist = false)
    private String teacherName;

    @TableField(exist = false)
    private String className;

    @TableField(exist = false)
    private String oldClassroomName;

    @TableField(exist = false)
    private String newClassroomName;
}
