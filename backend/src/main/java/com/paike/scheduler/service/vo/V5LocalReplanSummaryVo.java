package com.paike.scheduler.service.vo;

import lombok.Data;

import java.util.List;

@Data
public class V5LocalReplanSummaryVo {
    private Integer scopeItemCount;
    private Integer lockedCount;
    private Integer replanableCount;
    private Integer movedCount;
    private Integer failedCount;
    private List<Long> movedItemIds;
    private List<Long> failedItemIds;
    private List<String> logs;
}
