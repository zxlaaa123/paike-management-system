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
@TableName("system_audit_log")
public class SystemAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    private String operatorName;

    private String actionType;

    private String targetType;

    private Long targetId;

    private Long semesterId;

    private Long planId;

    private Integer success;

    private String beforeSummary;

    private String afterSummary;

    private String errorCode;

    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
