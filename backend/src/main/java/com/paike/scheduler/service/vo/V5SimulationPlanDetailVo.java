package com.paike.scheduler.service.vo;

import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import lombok.Data;

import java.util.List;

@Data
public class V5SimulationPlanDetailVo {
    private SchedulePlan plan;
    private List<SchedulePlanItem> items;
    private List<ScheduleScoreDetail> scoreDetails;
    private ScheduleRiskListVo risks;
    private V5SimulationCompareVo compare;
}
