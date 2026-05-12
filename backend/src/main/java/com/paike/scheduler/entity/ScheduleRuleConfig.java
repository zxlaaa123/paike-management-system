package com.paike.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("schedule_rule_config")
public class ScheduleRuleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleKey;

    private String ruleValue;

    private String ruleName;

    private String description;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
