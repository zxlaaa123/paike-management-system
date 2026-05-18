package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ScheduleRoomUtilizationChartVo {

    private Long planId;

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Long roomId;
        private String roomName;
        private String roomType;
        private Integer capacity;
        private Integer usedPeriods;
        private Integer totalPeriods;
        private BigDecimal utilizationRate;
    }
}
