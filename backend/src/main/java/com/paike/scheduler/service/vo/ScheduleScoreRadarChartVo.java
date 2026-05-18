package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ScheduleScoreRadarChartVo {

    private Long planId;

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private String name;
        private BigDecimal value;
        private String description;
    }
}
