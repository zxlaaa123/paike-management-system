package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 排课冲突报告下发项（M-16 第1批：替换 ScheduleConflictReport Entity 上的
 * {@code @TableField(exist = false) timeSlotName} view 字段）。
 *
 * 字段（13 个，按 Entity 声明序）：id / semesterId / reportNo / conflictType / objectType /
 * objectId / objectName / timeSlotId / relatedScheduleIds / description / suggestion /
 * createTime / timeSlotName。前 12 为持久化列，timeSlotName 为下发时计算的展示字段。
 *
 * 普通 POJO、保留 null 序列化（前端字段多为可选），不加 NON_NULL。JSON 字段名/类型与历史一致、
 * 前端零改动。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleConflictReportVo {

    private Long id;

    private Long semesterId;

    private String reportNo;

    private String conflictType;

    private String objectType;

    private Long objectId;

    private String objectName;

    private Long timeSlotId;

    private String relatedScheduleIds;

    private String description;

    private String suggestion;

    private LocalDateTime createTime;

    /** 下发时计算：时段 label / "全周" / 周几 / "-"。 */
    private String timeSlotName;
}
