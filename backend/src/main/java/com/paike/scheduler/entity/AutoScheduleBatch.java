package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("auto_schedule_batch")
public class AutoScheduleBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private Integer totalTaskCount;

    private Integer successTaskCount;

    private Integer failedTaskCount;

    private Integer generatedScheduleCount;

    private Integer clearOldSchedule;

    private String status;

    private String message;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;
}
