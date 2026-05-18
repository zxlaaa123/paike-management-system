package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ScheduleLockItemVo {

    private Long lockId;

    private String targetType;

    private Long planId;

    private Long planItemId;

    private Long scheduleId;

    private Long teachingTaskId;

    private String courseName;

    private String teacherName;

    private String className;

    private Integer weekDay;

    private String period;

    private String roomName;

    private String lockReason;

    private LocalDateTime createdAt;
}
