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
@TableName("schedule_conflict_report")
public class ScheduleConflictReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long semesterId;

    private String reportNo;

    private String conflictType;

    private String objectType;

    private Long objectId;

    private String objectName;

    private Long timeSlotId;

    private String relatedScheduleIds;

    private String description;

    private String suggestion;

    private LocalDateTime createTime;

    /** 关联字段 */
    @TableField(exist = false)
    private String timeSlotName;
}
