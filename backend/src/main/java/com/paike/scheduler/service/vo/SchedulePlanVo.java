package com.paike.scheduler.service.vo;

import com.paike.scheduler.entity.SchedulePlan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排课方案下发项（M-16 第7批：替换 SchedulePlan Entity 上的 2 个
 * {@code @TableField(exist = false)} view 字段 semesterName/strategyName）。
 *
 * 字段（22 个，按 Entity 声明序）：前 20 为持久化列
 * （id / sourcePlanId / sourceScheduleId / repairTaskId / semesterId / name / strategyType / planMode /
 * status / totalScore / scheduledCount / unscheduledCount / conflictCount / description / generatedBy /
 * generatedAt / appliedAt / createdAt / updatedAt / deleted），含 @TableLogic deleted（恒 0）；
 * 后 2 为展示字段（全库无 setter、恒 null，属漏填 bug——保留以维持 JSON 逐字段不变，后续可补填）。
 *
 * 该 VO 由 getById 返回，下发到方案详情端点 + ScheduleScoreController 得分查询。
 *
 * 普通 POJO、保留 null 序列化，不加 NON_NULL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulePlanVo {

    private Long id;
    private Long sourcePlanId;
    private Long sourceScheduleId;
    private Long repairTaskId;
    private Long semesterId;
    private String name;
    private String strategyType;
    private String planMode;
    private String status;
    private BigDecimal totalScore;
    private Integer scheduledCount;
    private Integer unscheduledCount;
    private Integer conflictCount;
    private String description;
    private String generatedBy;
    private LocalDateTime generatedAt;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** @TableLogic 软删除标记，恒 0。 */
    private Integer deleted;
    /** 全库无 setter、恒 null（疑漏填 bug）。 */
    private String semesterName;
    /** 全库无 setter、恒 null（疑漏填 bug）。 */
    private String strategyName;

    /** 从 Entity 逐字段拷贝 20 个持久化列。 */
    public static SchedulePlanVo fromEntity(SchedulePlan entity) {
        SchedulePlanVo vo = new SchedulePlanVo();
        vo.setId(entity.getId());
        vo.setSourcePlanId(entity.getSourcePlanId());
        vo.setSourceScheduleId(entity.getSourceScheduleId());
        vo.setRepairTaskId(entity.getRepairTaskId());
        vo.setSemesterId(entity.getSemesterId());
        vo.setName(entity.getName());
        vo.setStrategyType(entity.getStrategyType());
        vo.setPlanMode(entity.getPlanMode());
        vo.setStatus(entity.getStatus());
        vo.setTotalScore(entity.getTotalScore());
        vo.setScheduledCount(entity.getScheduledCount());
        vo.setUnscheduledCount(entity.getUnscheduledCount());
        vo.setConflictCount(entity.getConflictCount());
        vo.setDescription(entity.getDescription());
        vo.setGeneratedBy(entity.getGeneratedBy());
        vo.setGeneratedAt(entity.getGeneratedAt());
        vo.setAppliedAt(entity.getAppliedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setDeleted(entity.getDeleted());
        return vo;
    }
}
