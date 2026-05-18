package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduleTimeDensityChartVo {

    private Long planId;

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Integer weekDay;
        private Integer period;
        private Integer courseCount;
    }
}
