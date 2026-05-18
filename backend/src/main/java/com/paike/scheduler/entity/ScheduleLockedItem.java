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
@TableName("schedule_locked_item")
public class ScheduleLockedItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;

    private Long planId;

    private Long planItemId;

    private Long scheduleId;

    private String lockReason;

    private Integer activeFlag;

    private LocalDateTime unlockedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
