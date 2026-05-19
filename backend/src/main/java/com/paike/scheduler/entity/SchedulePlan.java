package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("schedule_plan")
public class SchedulePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sourcePlanId;

    private Long semesterId;

    private String name;

    private String strategyType;

    private String planMode;

    private String status;

    private BigDecimal totalScore;

    private Integer scheduledCount;

    private Integer unscheduledCount;

    private Integer conflictCount;

    private String description;

    private String generatedBy;

    private LocalDateTime generatedAt;

    private LocalDateTime appliedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 关联字段 */
    @TableField(exist = false)
    private String semesterName;

    /** 关联字段 */
    @TableField(exist = false)
    private String strategyName;
}
