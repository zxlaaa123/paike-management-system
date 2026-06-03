package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.ClassBalanceVo;
import com.paike.scheduler.service.vo.ClassroomUtilizationVo;
import com.paike.scheduler.service.vo.DashboardStatsVo;
import com.paike.scheduler.service.vo.PlanOverviewVo;
import com.paike.scheduler.service.vo.RefreshPlanRisksVo;
import com.paike.scheduler.service.vo.RefreshPlanSummaryVo;
import com.paike.scheduler.service.vo.TeacherWorkloadVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-14 阶段3 序列化守卫：锁定 ScheduleStatisticsService 5 方法 + 两个 refresh 端点把 Map 弱类型
 * 替换为 VO 后，JSON 字段名/值与历史输出逐一一致（纯机械替换、契约不变）。
 *
 * 重点覆盖（蓝图 §1.4 / §3.1 的坑）：
 * - {@code dailyPeriods} 动态键对象（{"1":3,...}）——TeacherWorkloadVo / ClassBalanceVo 均保留 Map；
 * - {@code courseCount} 是去重后的 size（Integer），不是 Set；
 * - {@code maxContinuousPeriods} 历史恒 0（占位字段）仍保留——TeacherWorkloadVo 10 字段；
 * - {@code dailyPeriods} 在 ClassBalanceVo 中保留——12 字段；
 * - PlanOverviewVo 17 字段：有方案 / 无方案（best/applied 块 null）两态都验；
 * - DashboardStatsVo 嵌套 PlanOverviewVo。
 *
 * 与阶段1/2 一致：走真实 wire 路径（writeValueAsString + readTree），BigDecimal 用数值比较，
 * findAndRegisterModules 支持 LocalDateTime。
 */
class M14Phase3VoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void teacherWorkloadVoKeepsAllTenFieldsWithDynamicDailyPeriods() throws Exception {
        Map<Integer, Long> dailyPeriods = new LinkedHashMap<>();
        dailyPeriods.put(1, 3L);
        dailyPeriods.put(3, 5L);
        // courseCount/classCount 已是去重后的 size（Integer），非 Set
        JsonNode json = toJson(new TeacherWorkloadVo(
                7L, 8, dailyPeriods, 5, 0, 2, 1, "张老师", "计算机系", "正常"));

        assertEquals(
                Set.of("teacherId", "totalPeriods", "dailyPeriods", "maxDailyPeriods",
                        "maxContinuousPeriods", "courseCount", "classCount",
                        "teacherName", "department", "evaluation"),
                fieldNames(json));
        assertEquals(7L, json.get("teacherId").asLong());
        assertEquals(8, json.get("totalPeriods").asInt());
        assertEquals(5, json.get("maxDailyPeriods").asInt());
        assertEquals(0, json.get("maxContinuousPeriods").asInt());
        // courseCount 是数量不是 Set
        assertTrue(json.get("courseCount").isNumber());
        assertEquals(2, json.get("courseCount").asInt());
        assertEquals(1, json.get("classCount").asInt());
        assertEquals("张老师", json.get("teacherName").asText());
        assertEquals("计算机系", json.get("department").asText());
        assertEquals("正常", json.get("evaluation").asText());

        // dailyPeriods 是动态键对象 {"1":3,"3":5}
        JsonNode daily = json.get("dailyPeriods");
        assertTrue(daily.isObject());
        assertEquals(3, daily.get("1").asInt());
        assertEquals(5, daily.get("3").asInt());
    }

    @Test
    void teacherWorkloadVoKeepsNullDepartment() throws Exception {
        JsonNode json = toJson(new TeacherWorkloadVo(
                7L, 0, new LinkedHashMap<>(), 0, 0, 0, 0, "未知", null, "无排课"));
        assertTrue(json.has("department"));
        assertTrue(json.get("department").isNull());
    }

    @Test
    void classroomUtilizationVoKeepsAllNineFields() throws Exception {
        JsonNode json = toJson(new ClassroomUtilizationVo(
                10L, "A101", "教学楼A", 60, "MULTIMEDIA", 12L, 40, new BigDecimal("30.0"), "低利用率"));
        assertEquals(
                Set.of("roomId", "roomName", "building", "capacity", "roomType",
                        "usedPeriods", "totalPeriods", "utilizationRate", "evaluation"),
                fieldNames(json));
        assertEquals(10L, json.get("roomId").asLong());
        assertEquals("A101", json.get("roomName").asText());
        assertEquals(60, json.get("capacity").asInt());
        assertEquals(12L, json.get("usedPeriods").asLong());
        assertEquals(40, json.get("totalPeriods").asInt());
        assertEquals(0, json.get("utilizationRate").decimalValue().compareTo(new BigDecimal("30.0")));
        assertEquals("低利用率", json.get("evaluation").asText());
    }

    @Test
    void classroomUtilizationVoKeepsNullBuildingAndRoomType() throws Exception {
        JsonNode json = toJson(new ClassroomUtilizationVo(
                10L, "A101", null, null, null, 0L, 40, BigDecimal.ZERO, "未使用"));
        assertTrue(json.get("building").isNull());
        assertTrue(json.get("capacity").isNull());
        assertTrue(json.get("roomType").isNull());
    }

    @Test
    void classBalanceVoKeepsAllTwelveFieldsWithDailyPeriods() throws Exception {
        Map<Integer, Long> dailyPeriods = new LinkedHashMap<>();
        dailyPeriods.put(1, 2L);
        dailyPeriods.put(2, 4L);
        JsonNode json = toJson(new ClassBalanceVo(
                100L, dailyPeriods, 6, "计科2101", 45, new BigDecimal("0.75"), "良好",
                2L, 4L, 0L, 0L, 0L));

        assertEquals(
                Set.of("classId", "dailyPeriods", "totalPeriods", "className", "studentCount",
                        "balanceScore", "evaluation",
                        "day1Periods", "day2Periods", "day3Periods", "day4Periods", "day5Periods"),
                fieldNames(json));
        assertEquals(100L, json.get("classId").asLong());
        assertEquals(6, json.get("totalPeriods").asInt());
        assertEquals("计科2101", json.get("className").asText());
        assertEquals(45, json.get("studentCount").asInt());
        assertEquals(0, json.get("balanceScore").decimalValue().compareTo(new BigDecimal("0.75")));
        assertEquals("良好", json.get("evaluation").asText());
        assertEquals(2L, json.get("day1Periods").asLong());
        assertEquals(4L, json.get("day2Periods").asLong());
        assertEquals(0L, json.get("day3Periods").asLong());

        // dailyPeriods 动态键对象保留
        JsonNode daily = json.get("dailyPeriods");
        assertTrue(daily.isObject());
        assertEquals(2, daily.get("1").asInt());
        assertEquals(4, daily.get("2").asInt());
    }

    @Test
    void planOverviewVoKeepsAllSeventeenFieldsWhenPlansExist() throws Exception {
        JsonNode json = toJson(new PlanOverviewVo(
                2L, 5, 3L, 1L, 1L,
                34L, "综合最优方案", new BigDecimal("88.50"), "COMPREHENSIVE",
                true, 34L, "综合最优方案", new BigDecimal("88.50"), LocalDateTime.of(2026, 6, 3, 10, 0, 0),
                120L, 2, 1));

        assertEquals(
                Set.of("semesterId", "totalPlans", "draftPlans", "appliedPlans", "abandonedPlans",
                        "bestPlanId", "bestPlanName", "bestPlanScore", "bestPlanStrategy",
                        "hasAppliedPlan", "appliedPlanId", "appliedPlanName", "appliedPlanScore",
                        "appliedPlanAppliedAt", "formalScheduleCount", "totalUnassignedTasks", "totalConflicts"),
                fieldNames(json));
        assertEquals(17, fieldNames(json).size());
        assertEquals(2L, json.get("semesterId").asLong());
        assertEquals(5, json.get("totalPlans").asInt());
        assertEquals(3L, json.get("draftPlans").asLong());
        assertEquals(34L, json.get("bestPlanId").asLong());
        assertEquals(0, json.get("bestPlanScore").decimalValue().compareTo(new BigDecimal("88.50")));
        assertTrue(json.get("hasAppliedPlan").asBoolean());
        assertEquals(120L, json.get("formalScheduleCount").asLong());
        assertEquals(2, json.get("totalUnassignedTasks").asInt());
        assertEquals(1, json.get("totalConflicts").asInt());
        assertFalse(json.get("appliedPlanAppliedAt").isNull());
    }

    @Test
    void planOverviewVoKeepsSeventeenFieldsAsNullWhenNoPlans() throws Exception {
        // 无最佳/无应用方案：best/applied 块为 null（普通 POJO 序列化为 null，前端 number|null 兼容）
        PlanOverviewVo vo = new PlanOverviewVo();
        vo.setSemesterId(2L);
        vo.setTotalPlans(0);
        vo.setDraftPlans(0L);
        vo.setAppliedPlans(0L);
        vo.setAbandonedPlans(0L);
        vo.setHasAppliedPlan(false);
        vo.setFormalScheduleCount(0L);
        vo.setTotalUnassignedTasks(0);
        vo.setTotalConflicts(0);
        JsonNode json = toJson(vo);

        // 17 个键仍全部存在（普通 POJO 不省略 null）
        assertEquals(17, fieldNames(json).size());
        assertTrue(json.get("bestPlanId").isNull());
        assertTrue(json.get("bestPlanName").isNull());
        assertTrue(json.get("bestPlanScore").isNull());
        assertTrue(json.get("bestPlanStrategy").isNull());
        assertTrue(json.get("appliedPlanId").isNull());
        assertTrue(json.get("appliedPlanName").isNull());
        assertTrue(json.get("appliedPlanScore").isNull());
        assertTrue(json.get("appliedPlanAppliedAt").isNull());
        assertFalse(json.get("hasAppliedPlan").asBoolean());
    }

    @Test
    void dashboardStatsVoNestsPlanOverview() throws Exception {
        PlanOverviewVo overview = new PlanOverviewVo();
        overview.setSemesterId(2L);
        overview.setTotalPlans(5);
        overview.setDraftPlans(3L);
        overview.setAppliedPlans(1L);
        overview.setAbandonedPlans(1L);
        overview.setHasAppliedPlan(true);
        overview.setFormalScheduleCount(120L);
        overview.setTotalUnassignedTasks(0);
        overview.setTotalConflicts(0);
        JsonNode json = toJson(new DashboardStatsVo(50L, 30L, 20L, overview));

        assertEquals(Set.of("teacherCount", "classCount", "classroomCount", "v3Overview"), fieldNames(json));
        assertEquals(50L, json.get("teacherCount").asLong());
        assertEquals(30L, json.get("classCount").asLong());
        assertEquals(20L, json.get("classroomCount").asLong());

        // 嵌套 PlanOverviewVo 17 字段
        JsonNode nested = json.get("v3Overview");
        assertTrue(nested.isObject());
        assertEquals(17, fieldNames(nested).size());
        assertEquals(2L, nested.get("semesterId").asLong());
        assertEquals(5, nested.get("totalPlans").asInt());
    }

    @Test
    void refreshPlanSummaryVoMatchesLegacyJson() throws Exception {
        // 历史：LinkedHashMap{planId, refreshed:true, message}
        JsonNode json = toJson(new RefreshPlanSummaryVo(34L, true, "方案质量分析已刷新"));
        assertEquals(Set.of("planId", "refreshed", "message"), fieldNames(json));
        assertEquals(34L, json.get("planId").asLong());
        assertTrue(json.get("refreshed").asBoolean());
        assertEquals("方案质量分析已刷新", json.get("message").asText());
    }

    @Test
    void refreshPlanRisksVoMatchesLegacyJson() throws Exception {
        // 历史：LinkedHashMap{planId, riskCount(来自 ScheduleRiskListVo.getRiskCount, Integer), message}
        JsonNode json = toJson(new RefreshPlanRisksVo(34L, 7, "风险诊断已刷新"));
        assertEquals(Set.of("planId", "riskCount", "message"), fieldNames(json));
        assertEquals(34L, json.get("planId").asLong());
        assertEquals(7, json.get("riskCount").asInt());
        assertEquals("风险诊断已刷新", json.get("message").asText());
    }

    /** 走真实 wire 路径序列化，避免 valueToTree 的 BigDecimal 归一化。 */
    private JsonNode toJson(Object vo) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(vo));
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }
}
