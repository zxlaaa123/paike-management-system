package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("time_slot")
public class TimeSlot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer dayOfWeek;

    private Integer periodNo;

    private String timeLabel;

    private Integer sortOrder;
}
