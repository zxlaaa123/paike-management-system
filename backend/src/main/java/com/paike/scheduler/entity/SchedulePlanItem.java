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
@TableName("schedule_plan_item")
public class SchedulePlanItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long semesterId;

    private Long teachingTaskId;

    private Long teacherId;

    private Long classId;

    private Long courseId;

    private Long classroomId;

    private Integer weekday;

    private Integer startPeriod;

    private Integer endPeriod;

    private String weekType;

    /** 连续周段起始周（闭区间，默认1，V10 连续周段支持） */
    private Integer startWeek;

    /** 连续周段结束周（闭区间，默认20，V10 连续周段支持） */
    private Integer endWeek;

    private BigDecimal score;

    private Integer conflictFlag;

    private String conflictReason;

    private String sourceType;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
