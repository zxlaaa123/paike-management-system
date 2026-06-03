package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.SchedulePlanVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-16 第7批序列化守卫：锁定 SchedulePlan Entity 上 2 个
 * {@code @TableField(exist = false)} view 字段（semesterName/strategyName）
 * 删除、改由 SchedulePlanVo 承载后，getById 下发 JSON 与历史逐字段一致。
 *
 * 本批覆盖：
 * - 22 字段集（20 持久化〔含 @TableLogic deleted 恒 0、BigDecimal totalScore〕+ 2 view 恒 null）；
 * - view 字段恒 null（全库无 setter，属漏填 bug）；
 * - deleted 恒 0。
 *
 * 走真实 wire 路径（writeValueAsString + readTree），findAndRegisterModules 支持 LocalDateTime。
 */
class M16PlanVoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "id", "sourcePlanId", "sourceScheduleId", "repairTaskId", "semesterId",
            "name", "strategyType", "planMode", "status", "totalScore",
            "scheduledCount", "unscheduledCount", "conflictCount", "description",
            "generatedBy", "generatedAt", "appliedAt", "createdAt", "updatedAt", "deleted",
            "semesterName", "strategyName");

    @Test
    void planVoKeepsAllTwentyTwoFields() throws Exception {
        JsonNode json = toJson(new SchedulePlanVo(
                1L, null, null, null, 2L,
                "测试方案", "DEFAULT", "AUTO", "DRAFT",
                new BigDecimal("95.5"), 10, 2, 1, "描述",
                "V4_BATCH", LocalDateTime.of(2026, 6, 3, 10, 0, 0),
                null, LocalDateTime.of(2026, 6, 3, 10, 0, 0),
                LocalDateTime.of(2026, 6, 3, 10, 0, 0), 0,
                null, null));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals(22, fieldNames(json).size());
        assertEquals(0, new BigDecimal("95.5").compareTo(json.get("totalScore").decimalValue()));
        assertEquals(0, json.get("deleted").asInt());
        assertTrue(json.get("semesterName").isNull());
        assertTrue(json.get("strategyName").isNull());
    }

    @Test
    void planVoKeepsNullTimestampsAndViewFields() throws Exception {
        SchedulePlanVo vo = new SchedulePlanVo();
        vo.setId(2L);
        vo.setName("空方案");
        vo.setDeleted(0);
        JsonNode json = toJson(vo);

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertTrue(json.get("semesterName").isNull());
        assertTrue(json.get("strategyName").isNull());
        assertTrue(json.get("createdAt").isNull());
        assertTrue(json.get("updatedAt").isNull());
        assertTrue(json.get("generatedAt").isNull());
        assertTrue(json.get("appliedAt").isNull());
        assertEquals(0, json.get("deleted").asInt());
    }

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
