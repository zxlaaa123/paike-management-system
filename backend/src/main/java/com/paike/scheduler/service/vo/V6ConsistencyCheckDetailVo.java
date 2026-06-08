package com.paike.scheduler.service.vo;

import com.paike.scheduler.entity.ScheduleConsistencyCheck;
import lombok.Data;

import java.util.List;

@Data
public class V6ConsistencyCheckDetailVo {
    private ScheduleConsistencyCheck record;
    private V5ConsistencyCheckReportVo report;
    private List<V5ConsistencyIssueVo> issues;
}

