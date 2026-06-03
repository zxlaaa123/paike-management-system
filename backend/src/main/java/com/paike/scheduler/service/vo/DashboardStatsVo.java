package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页统计（M-14 阶段3：替换 ScheduleStatisticsService.dashboardStats 原先 Map 弱类型返回）。
 *
 * 字段与历史 JSON 完全一致（4 字段，按原 LinkedHashMap 插入序）：teacherCount / classCount /
 * classroomCount / v3Overview（嵌套 PlanOverviewVo）。
 *
 * - 三个 count 来自 selectCount，故为 Long。
 * - v3Overview 历史是嵌套的 planOverview Map，现为强类型 PlanOverviewVo。
 * - 普通 POJO、保留 null 序列化，不加 NON_NULL。
 *
 * 前端 DashboardStats 4 字段（含 v3Overview: PlanOverview 嵌套）逐字段对齐。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsVo {

    private Long teacherCount;

    private Long classCount;

    private Long classroomCount;

    private PlanOverviewVo v3Overview;
}
