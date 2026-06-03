package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 班级均衡度统计项（M-14 阶段3：替换 ScheduleStatisticsService.classBalance 原先
 * List 中的 Map 弱类型元素）。
 *
 * 字段与历史 JSON 完全一致（12 字段，按原 LinkedHashMap 插入序）：classId / dailyPeriods /
 * totalPeriods / className / studentCount / balanceScore / evaluation / day1Periods..day5Periods。
 *
 * 说明：
 * - dailyPeriods 为聚合中间产物（周几 -> 节次和），历史被一并 put 进结果输出，保留 Map 弱类型
 *   以维持 JSON 逐字节不变。前端 ClassBalanceItem 不读它（只读派生的 day1-5Periods）。
 * - balanceScore = 1 -（标准差/平均），BigDecimal scale 2。
 * - day1-5Periods 来自 dailyPeriods.getOrDefault(day, 0L)，故为 Long。
 * - 普通 POJO、保留 null 序列化，不加 NON_NULL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassBalanceVo {

    private Long classId;

    /** 周几 -> 当日节次和，动态键 Map 弱类型返回（前端不读，保留以维持 JSON 不变）。 */
    private Map<Integer, Long> dailyPeriods;

    private Integer totalPeriods;

    private String className;

    private Integer studentCount;

    private BigDecimal balanceScore;

    private String evaluation;

    private Long day1Periods;

    private Long day2Periods;

    private Long day3Periods;

    private Long day4Periods;

    private Long day5Periods;
}
