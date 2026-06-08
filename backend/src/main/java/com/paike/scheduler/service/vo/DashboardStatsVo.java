package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页统计（M-14 阶段3：替换 ScheduleStatisticsService.dashboardStats 原先 Map 弱类型返回）。
 *
 * 首页统计：基础资源数量 + 当前学期排课治理摘要。
 *
 * - 三个 count 来自 selectCount，故为 Long。
 * - v3Overview 历史是嵌套的 planOverview Map，现为强类型 PlanOverviewVo。
 * - 普通 POJO、保留 null 序列化，不加 NON_NULL。
 *
 * 前端 DashboardStats 逐字段对齐。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsVo {

    private Long teacherCount;

    private Long classCount;

    private Long classroomCount;

    private Long courseCount;

    private Long teachingTaskCount;

    private Integer totalUnassignedTasks;

    private Integer totalConflicts;

    private Boolean hasAppliedPlan;

    private String governanceSummary;

    private PlanOverviewVo v3Overview;
}
