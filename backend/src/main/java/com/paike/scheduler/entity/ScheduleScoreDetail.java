package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("schedule_score_detail")
public class ScheduleScoreDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long semesterId;

    private String ruleCode;

    private String ruleType;

    private String ruleName;

    private BigDecimal score;

    private BigDecimal maxScore;

    private Integer violationCount;

    private String detailMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
