package com.paike.scheduler.service.vo;

import lombok.Data;

@Data
public class V5SimulationItemChangeVo {
    private Long sourceItemId;
    private Long simulationItemId;
    private String courseName;
    private String teacherName;
    private String className;
    private Integer beforeWeekday;
    private Integer beforeStartPeriod;
    private Integer beforeEndPeriod;
    private Long beforeClassroomId;
    private String beforeClassroomName;
    private Integer afterWeekday;
    private Integer afterStartPeriod;
    private Integer afterEndPeriod;
    private Long afterClassroomId;
    private String afterClassroomName;
    private Integer conflictFlag;
    private String conflictReason;
}
