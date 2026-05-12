package com.paike.scheduler.controller;

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
}
