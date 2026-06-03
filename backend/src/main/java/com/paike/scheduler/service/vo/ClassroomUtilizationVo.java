package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 教室利用率统计项（M-14 阶段3：替换 ScheduleStatisticsService.classroomUtilization 原先
 * List 中的 Map 弱类型元素）。
 *
 * 字段与历史 JSON 完全一致（9 字段，按原 LinkedHashMap 插入序）：roomId / roomName / building /
 * capacity / roomType / usedPeriods / totalPeriods / utilizationRate / evaluation。
 *
 * - usedPeriods 来自 Map.merge 累加（Long）；totalPeriods 来自阈值配置（int）。
 * - utilizationRate 为 BigDecimal（保留 scale，HALF_UP 1 位小数）。
 * - 普通 POJO、保留 null 序列化（building/roomType 可能为 null），不加 NON_NULL。
 *
 * 前端 ClassroomUtilizationItem 9 字段逐字段对齐。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomUtilizationVo {

    private Long roomId;

    private String roomName;

    private String building;

    private Integer capacity;

    private String roomType;

    private Long usedPeriods;

    private Integer totalPeriods;

    private BigDecimal utilizationRate;

    private String evaluation;
}
