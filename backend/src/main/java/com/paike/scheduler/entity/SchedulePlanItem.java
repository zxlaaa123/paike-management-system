package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
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

    private BigDecimal score;

    private Integer conflictFlag;

    private String conflictReason;

    private String sourceType;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 关联字段 */
    @TableField(exist = false)
    private String courseName;

    @TableField(exist = false)
    private String teacherName;

    @TableField(exist = false)
    private String className;

    @TableField(exist = false)
    private String roomName;

    @TableField(exist = false)
    private String timeLabel;
}
