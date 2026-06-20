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

    /** 连续周段起始周（闭区间，默认1，V10 连续周段支持，导出与网格显示用） */
    private Integer startWeek;

    /** 连续周段结束周（闭区间，默认20，V10 连续周段支持，导出与网格显示用） */
    private Integer endWeek;
}
