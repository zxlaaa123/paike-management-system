package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("schedule")
public class Schedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long semesterId;

    private Long teachingTaskId;

    /** 注意：以下三个字段（courseId/teacherId/classId）与 TeachingTask 表的对应字段冗余。
     *  它们从 TeachingTask 复制而来，创建后不再同步，若 TeachingTask 变更会导致不一致。
     *  查询时优先通过 teachingTaskId JOIN 获取最新值，而非直接使用这些冗余字段。
     *  当前保留是为了冲突检测查询的便利性（避免每次 JOIN TeachingTask 表）。
     */
    private Long courseId;

    private Long teacherId;

    private Long classId;

    private Long timeSlotId;

    /** 周次类型：ALL全周、ODD单周、EVEN双周（V9 单双周支持） */
    private String weekType;

    private Long classroomId;

    private String sourceType;

    private Long batchId;

    private Long planId;

    @TableLogic
    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
