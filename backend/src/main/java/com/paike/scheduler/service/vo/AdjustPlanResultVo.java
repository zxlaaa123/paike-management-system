package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 方案明细调整结果（M-14 阶段2：替换 SchedulePlanService.adjustPlanItem 原先的 Map 弱类型返回）。
 * 字段与历史 JSON 完全一致（9 字段）：itemId / planId / beforeScore / afterScore /
 * conflictFlag / conflictReason / syncFormalSchedule / scheduleId / message。
 *
 * beforeScore / afterScore / conflictReason / scheduleId 可能为 null，保留 null 序列化
 * （对齐原 LinkedHashMap 行为，前端 adjustSchedulePlanItem 类型亦为 number|null / string|null）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdjustPlanResultVo {

    private Long itemId;

    private Long planId;

    private BigDecimal beforeScore;

    private BigDecimal afterScore;

    private Integer conflictFlag;

    private String conflictReason;

    private Boolean syncFormalSchedule;

    private Long scheduleId;

    private String message;
}
