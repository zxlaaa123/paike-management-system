package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
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

    @TableLogic
    private Integer deleted;
}
