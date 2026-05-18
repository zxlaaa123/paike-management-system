package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduleRiskIssueVo {

    private Long id;

    private String riskType;

    private String riskTypeName;

    private String level;

    private String title;

    private String description;

    private Long relatedTeacherId;

    private String relatedTeacherName;

    private Long relatedClassId;

    private String relatedClassName;

    private Long relatedRoomId;

    private String relatedRoomName;

    private Long relatedCourseId;

    private String relatedCourseName;

    private Integer weekDay;

    private String period;

    private String suggestion;

    private Boolean resolved;

    private String affectedObjects;

    private List<Long> relatedItemIds;

    private List<String> detailLines;
}
