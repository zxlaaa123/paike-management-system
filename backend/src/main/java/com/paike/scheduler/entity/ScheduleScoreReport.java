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
@TableName("schedule_score_report")
public class ScheduleScoreReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long semesterId;

    private Integer score;

    private String grade;

    private Integer conflictCount;

    private Integer unfinishedTaskCount;

    private Integer teacherOverloadCount;

    private Integer classOverloadCount;

    private Integer fridayAfternoonCount;

    @TableField("deduction_detail")
    private String deductionDetail;

    private String suggestion;

    private LocalDateTime createTime;

    private String gradeName;
}
