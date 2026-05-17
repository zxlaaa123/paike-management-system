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
@TableName("schedule_rule_weight")
public class ScheduleRuleWeight {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long semesterId;

    private String strategyType;

    private String ruleCode;

    private String ruleName;

    private String ruleType;

    private BigDecimal weight;

    private Integer enabled;

    private String description;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
