package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ScheduleAdjustmentLogListVo {

    private Long planId;

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Long id;
        private String targetType;
        private String operationType;
        private String courseName;
        private Integer beforeWeekDay;
        private String beforePeriod;
        private String beforeRoomName;
        private Integer afterWeekDay;
        private String afterPeriod;
        private String afterRoomName;
        private String remark;
        private LocalDateTime createdAt;
    }
}
