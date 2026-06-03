package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未排任务原因汇总项（M-14 收敛：替换 SchedulePlanExplainService.summarizeUnassignedTasks
 * 原先 List 中的 Map 弱类型元素）。
 * 字段与历史 JSON 完全一致：reasonCode / reasonName / count。
 * 前端 UnassignedSummaryItem[] 逐字段对齐（reasonCode/reasonName: string，count: number）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnassignedSummaryVo {

    /** 原因代码，如 NO_AVAILABLE_CLASSROOM。 */
    private String reasonCode;

    /** 原因中文名。 */
    private String reasonName;

    /** 该原因下的未排任务数（来自 Collectors.counting()，故为 Long）。 */
    private Long count;
}
