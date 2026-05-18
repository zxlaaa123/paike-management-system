package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduleTeacherHoursChartVo {

    private Long planId;

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Long teacherId;
        private String teacherName;
        private Integer totalHours;
        private Integer courseCount;
    }
}
