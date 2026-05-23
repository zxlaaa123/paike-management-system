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
@TableName("unscheduled_task")
public class UnscheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long semesterId;

    private Long taskId;

    private Long courseId;

    private Long teacherId;

    private Long classId;

    private Integer requiredSlots;

    private Integer scheduledSlots;

    private Integer remainingSlots;

    private String reasonType;

    private String reasonMessage;

    private LocalDateTime createTime;

    /** 关联字段 */
    @TableField(exist = false)
    private String courseName;

    @TableField(exist = false)
    private String teacherName;

    @TableField(exist = false)
    private String className;

    @TableField(exist = false)
    private String batchNo;
}
