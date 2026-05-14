package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("teaching_task")
public class TeachingTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long semesterId;

    private Long courseId;

    private Long teacherId;

    private Long classId;

    private Integer weeklyHours;

    private Integer needContinuous;

    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 关联字段，不映射到数据库 */
    @TableField(exist = false)
    private String courseName;

    @TableField(exist = false)
    private String teacherName;

    @TableField(exist = false)
    private String className;

    @TableField(exist = false)
    private Integer scheduledSlots;
}
