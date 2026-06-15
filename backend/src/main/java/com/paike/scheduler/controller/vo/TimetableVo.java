package com.paike.scheduler.controller.vo;

import lombok.Data;

@Data
public class TimetableVo {

    private Long scheduleId;

    private Long timeSlotId;

    private Integer dayOfWeek;

    private Integer period;

    private String timeSlotName;

    private String courseName;

    private String courseType;

    private String teacherName;

    private String className;

    private String classroomName;

    private String building;

    /** 周次类型：ALL全周、ODD单周、EVEN双周（V9 单双周支持，导出与网格显示用） */
    private String weekType;
}
