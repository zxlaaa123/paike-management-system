package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 教师工作量统计项（M-14 阶段3：替换 ScheduleStatisticsService.teacherWorkload 原先
 * List 中的 Map 弱类型元素）。
 *
 * 字段与历史 JSON 完全一致（10 字段，按原 LinkedHashMap 插入序）：teacherId / totalPeriods /
 * dailyPeriods / maxDailyPeriods / maxContinuousPeriods / courseCount / classCount /
 * teacherName / department / evaluation。
 *
 * 说明：
 * - dailyPeriods 为动态键（周几 -> 节次和），保留 Map 弱类型不 VO 化，JSON 序列化为 {"1":3,...}，
 *   前端 Record&lt;number,number&gt;。
 * - maxContinuousPeriods 历史恒为 0（占位、未实现连续节次统计），保留以维持 JSON 逐字节不变。
 * - courseCount / classCount 为去重后的数量（原先循环里先放 Set 再替换为 size），VO 直接存 Integer。
 * - 普通 POJO、保留 null 序列化（department 可能为 null），不加 NON_NULL。
 *
 * 前端 TeacherWorkloadItem 读 9 字段（不读 maxContinuousPeriods），多发字段无害。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeacherWorkloadVo {

    private Long teacherId;

    private Integer totalPeriods;

    /** 周几 -> 当日节次和，动态键 Map 弱类型返回，JSON 为 {"1":3,...}。 */
    private Map<Integer, Long> dailyPeriods;

    private Integer maxDailyPeriods;

    /** 历史恒为 0（占位字段），保留以维持 JSON 不变。 */
    private Integer maxContinuousPeriods;

    private Integer courseCount;

    private Integer classCount;

    private String teacherName;

    private String department;

    private String evaluation;
}
