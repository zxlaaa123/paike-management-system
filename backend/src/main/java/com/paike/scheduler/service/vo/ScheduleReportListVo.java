package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduleReportListVo {

    private Long planId;

    private Long semesterId;

    private List<ScheduleReportItemVo> items;
}
