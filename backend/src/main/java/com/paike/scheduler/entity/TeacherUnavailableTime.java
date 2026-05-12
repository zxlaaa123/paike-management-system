package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@TableName("teacher_unavailable_time")
public class TeacherUnavailableTime {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "教师不能为空")
    private Long teacherId;

    @NotNull(message = "时间段不能为空")
    private Long timeSlotId;

    private String reason;

    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 关联字段 */
    @TableField(exist = false)
    private String teacherName;

    @TableField(exist = false)
    private String department;

    @TableField(exist = false)
    private String timeSlotName;

    @TableField(exist = false)
    private Integer dayOfWeek;

    @TableField(exist = false)
    private Integer periodNo;
}
