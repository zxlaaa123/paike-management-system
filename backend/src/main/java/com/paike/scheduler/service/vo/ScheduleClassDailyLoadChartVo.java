package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduleClassDailyLoadChartVo {

    private Long planId;

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Long classId;
        private String className;
        private Integer weekDay;
        private Integer lessonCount;
    }
}
