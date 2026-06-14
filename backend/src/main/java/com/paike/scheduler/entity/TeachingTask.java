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
@TableName("teaching_task")
public class TeachingTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long semesterId;

    private Long courseId;

    private Long teacherId;

    private Long classId;

    private Integer weeklyHours;

    /** 周次类型：ALL全周、ODD单周、EVEN双周（V9 单双周支持） */
    private String weekType;

    private Integer needContinuous;

    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
