package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.ScheduleAdjustLogVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-16 第4批序列化守卫：锁定 ScheduleAdjustLog Entity 上 5 个
 * {@code @TableField(exist = false)} view 字段（courseName/teacherName/className/oldClassroomName/newClassroomName）
 * 删除、改由 ScheduleAdjustLogVo 承载后，listAdjustLogs 下发 JSON（调整日志列表端点 /
 * V4ScheduleSourceService 方案调整日志 / V5SimulationPlanDetailVo.adjustLogs 嵌套列表）与历史逐字段一致。
 *
 * 本批覆盖：
 * - 24 字段集（19 持久化〔含 @TableLogic deleted、createdAt、beforeScore/afterScore〕+ 5 view）；
 * - view 字段填充态 / 关联缺失 null 态；
 * - deleted 恒 0（严格逐字段，镜像 Entity 当前序列化）。
 *
 * 走真实 wire 路径（writeValueAsString + readTree），findAndRegisterModules 支持 LocalDateTime。
 */
class M16AdjustLogVoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "id", "planId", "scheduleId", "semesterId", "teachingTaskId",
            "oldClassroomId", "oldWeekday", "oldStartPeriod", "oldEndPeriod",
            "newClassroomId", "newWeekday", "newStartPeriod", "newEndPeriod",
            "beforeScore", "afterScore", "conflictFlag", "adjustReason", "createdAt", "deleted",
            "courseName", "teacherName", "className", "oldClassroomName", "newClassroomName");

    @Test
    void adjustLogVoKeepsAllTwentyFourFieldsWithRelations() throws Exception {
        JsonNode json = toJson(new ScheduleAdjustLogVo(
                1L, 10L, 20L, 2L, 30L,
                40L, 1, 1, 2,
                41L, 3, 5, 6,
                new BigDecimal("88.5"), new BigDecimal("92.0"), 0, "教师禁排冲突",
                LocalDateTime.of(2026, 6, 3, 10, 0, 0), 0,
                "高等数学", "张老师", "计科2401", "A101", "B202"));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals(24, fieldNames(json).size());
        assertEquals(1L, json.get("id").asLong());
        assertEquals(10L, json.get("planId").asLong());
        assertEquals(20L, json.get("scheduleId").asLong());
        assertEquals(30L, json.get("teachingTaskId").asLong());
        assertEquals(0, new BigDecimal("88.5").compareTo(json.get("beforeScore").decimalValue()));
        assertEquals(0, new BigDecimal("92.0").compareTo(json.get("afterScore").decimalValue()));
        assertEquals(0, json.get("conflictFlag").asInt());
        assertEquals("教师禁排冲突", json.get("adjustReason").asText());
        assertEquals(0, json.get("deleted").asInt());
        assertEquals("高等数学", json.get("courseName").asText());
        assertEquals("张老师", json.get("teacherName").asText());
        assertEquals("计科2401", json.get("className").asText());
        assertEquals("A101", json.get("oldClassroomName").asText());
        assertEquals("B202", json.get("newClassroomName").asText());
    }

    @Test
    void adjustLogVoKeepsNullRelationFields() throws Exception {
        // 关联缺失（task/classroom 查不到）时 5 个 view 字段为 null：普通 POJO 不省略键
        ScheduleAdjustLogVo vo = new ScheduleAdjustLogVo();
        vo.setId(2L);
        vo.setPlanId(11L);
        vo.setTeachingTaskId(31L);
        vo.setDeleted(0);
        JsonNode json = toJson(vo);

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertTrue(json.get("courseName").isNull());
        assertTrue(json.get("teacherName").isNull());
        assertTrue(json.get("className").isNull());
        assertTrue(json.get("oldClassroomName").isNull());
        assertTrue(json.get("newClassroomName").isNull());
        assertTrue(json.get("createdAt").isNull());
        assertTrue(json.get("beforeScore").isNull());
        assertTrue(json.get("afterScore").isNull());
        assertEquals(0, json.get("deleted").asInt());
    }

    /** 走真实 wire 路径序列化。 */
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
