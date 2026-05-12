package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("schedule")
public class Schedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teachingTaskId;

    private Long courseId;

    private Long teacherId;

    private Long classId;

    private Long timeSlotId;

    private Long classroomId;

    private String sourceType;

    private Long batchId;

    @TableLogic
    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 关联字段 */
    @TableField(exist = false)
    private String courseName;

    @TableField(exist = false)
    private String teacherName;

    @TableField(exist = false)
    private String className;

    @TableField(exist = false)
    private String timeLabel;

    @TableField(exist = false)
    private Integer dayOfWeek;

    @TableField(exist = false)
    private Integer periodNo;

    @TableField(exist = false)
    private String roomName;

    @TableField(exist = false)
    private String building;

    /** V2 排课来源 */
    @TableField(exist = false)
    private String sourceTypeName;

    /** V2 自动排课批次号 */
    @TableField(exist = false)
    private String batchNo;
}
