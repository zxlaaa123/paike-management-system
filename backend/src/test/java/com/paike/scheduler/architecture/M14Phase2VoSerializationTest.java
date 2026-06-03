package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.AdjustPlanResultVo;
import com.paike.scheduler.service.vo.ApplyPlanResultVo;
import com.paike.scheduler.service.vo.ComparePlanVo;
import com.paike.scheduler.service.vo.CompareResultVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-14 阶段2 序列化守卫：锁定 apply/adjust/rollback/compare 4 个端点把 Map 弱类型替换为 VO 后，
 * JSON 字段名/值与历史输出一致（纯机械替换、契约不变）。
 *
 * 重点：
 * - {@link ApplyPlanResultVo} 的 message 用 {@code @JsonInclude(NON_NULL)}——验「省略」与「存在」两态；
 * - {@link AdjustPlanResultVo}/{@link ComparePlanVo} 保留 null 字段序列化（对齐原 LinkedHashMap）；
 * - {@link CompareResultVo} 嵌套 {@code ComparePlanVo[]} 结构。
 *
 * 与阶段1 一致：走真实 wire 路径（writeValueAsString + readTree），BigDecimal 用数值比较。
 */
class M14Phase2VoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void adjustPlanResultVoKeepsAllNineFieldsIncludingNulls() throws Exception {
        // 同步正式课表分支：9 字段全有值
        JsonNode synced = toJson(new AdjustPlanResultVo(
                100L, 7L, new BigDecimal("85.50"), new BigDecimal("90.00"),
                1, "教师时间冲突", true, 200L, "已同步正式课表"));
        assertEquals(
                Set.of("itemId", "planId", "beforeScore", "afterScore", "conflictFlag",
                        "conflictReason", "syncFormalSchedule", "scheduleId", "message"),
                fieldNames(synced));
        assertEquals(100L, synced.get("itemId").asLong());
        assertEquals(7L, synced.get("planId").asLong());
        assertEquals(0, synced.get("beforeScore").decimalValue().compareTo(new BigDecimal("85.50")));
        assertEquals(0, synced.get("afterScore").decimalValue().compareTo(new BigDecimal("90.00")));
        assertEquals(1, synced.get("conflictFlag").asInt());
        assertEquals("教师时间冲突", synced.get("conflictReason").asText());
        assertTrue(synced.get("syncFormalSchedule").asBoolean());
        assertEquals(200L, synced.get("scheduleId").asLong());
        assertEquals("已同步正式课表", synced.get("message").asText());

        // 仅更新草稿分支：beforeScore/afterScore/conflictReason/scheduleId 为 null，仍保留这 9 个键
        JsonNode draft = toJson(new AdjustPlanResultVo(
                100L, 7L, null, null, 0, null, false, null, "仅更新方案草稿"));
        assertEquals(
                Set.of("itemId", "planId", "beforeScore", "afterScore", "conflictFlag",
                        "conflictReason", "syncFormalSchedule", "scheduleId", "message"),
                fieldNames(draft));
        assertTrue(draft.get("beforeScore").isNull());
        assertTrue(draft.get("conflictReason").isNull());
        assertTrue(draft.get("scheduleId").isNull());
        assertFalse(draft.get("syncFormalSchedule").asBoolean());
        assertEquals("仅更新方案草稿", draft.get("message").asText());
    }

    @Test
    void applyPlanResultVoOmitsMessageWhenNull() throws Exception {
        // apply / applySimulation / rollback 委派分支：4 字段，message 省略
        JsonNode applied = toJson(new ApplyPlanResultVo(7L, 1L, 42, LocalDateTime.of(2026, 6, 3, 10, 0, 0)));
        assertEquals(Set.of("planId", "semesterId", "appliedCount", "appliedAt"), fieldNames(applied));
        assertEquals(7L, applied.get("planId").asLong());
        assertEquals(1L, applied.get("semesterId").asLong());
        assertEquals(42, applied.get("appliedCount").asInt());
        assertFalse(applied.get("appliedAt").isNull());
    }

    @Test
    void applyPlanResultVoIncludesMessageOnRollbackShortCircuit() throws Exception {
        // rollback「目标方案已是当前应用方案」短路分支：第 5 个 message 字段出现
        JsonNode rolled = toJson(new ApplyPlanResultVo(
                7L, 1L, 0, LocalDateTime.of(2026, 6, 3, 10, 0, 0), "目标方案已是当前应用方案"));
        assertEquals(Set.of("planId", "semesterId", "appliedCount", "appliedAt", "message"), fieldNames(rolled));
        assertEquals(0, rolled.get("appliedCount").asInt());
        assertEquals("目标方案已是当前应用方案", rolled.get("message").asText());
    }

    @Test
    void comparePlanVoKeepsAllTwelveFields() throws Exception {
        JsonNode plan = toJson(new ComparePlanVo(
                7L, "方案A", "COMPREHENSIVE", "综合最优", "DRAFT",
                new BigDecimal("88.00"), 100, 5, 2, 1, 3, LocalDateTime.of(2026, 6, 3, 10, 0, 0)));
        assertEquals(
                Set.of("planId", "planName", "strategyType", "strategyName", "status", "totalScore",
                        "scheduledCount", "unscheduledCount", "conflictCount", "hardViolationCount",
                        "softViolationCount", "generatedAt"),
                fieldNames(plan));
        assertEquals("方案A", plan.get("planName").asText());
        assertEquals("综合最优", plan.get("strategyName").asText());
        assertEquals(0, plan.get("totalScore").decimalValue().compareTo(new BigDecimal("88.00")));
        assertEquals(100, plan.get("scheduledCount").asInt());
        assertEquals(2, plan.get("conflictCount").asInt());
    }

    @Test
    void compareResultVoNestsComparePlanArray() throws Exception {
        ComparePlanVo plan = new ComparePlanVo(
                7L, "方案A", "COMPREHENSIVE", "综合最优", "DRAFT",
                new BigDecimal("88.00"), 100, 0, 0, 0, 0, LocalDateTime.of(2026, 6, 3, 10, 0, 0));
        JsonNode result = toJson(new CompareResultVo(1L, List.of(plan), 7L, "方案A 总分最高（88.00分），所有任务均已排入，无冲突，推荐应用为正式课表。"));
        assertEquals(Set.of("semesterId", "plans", "bestPlanId", "summary"), fieldNames(result));
        assertEquals(1L, result.get("semesterId").asLong());
        assertEquals(7L, result.get("bestPlanId").asLong());
        assertTrue(result.get("plans").isArray());
        assertEquals(1, result.get("plans").size());
        assertEquals(7L, result.get("plans").get(0).get("planId").asLong());
        assertEquals(12, fieldNames(result.get("plans").get(0)).size());
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
