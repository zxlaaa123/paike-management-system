package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.SchedulePlanItemVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-16 第6批序列化守卫：锁定 SchedulePlanItem Entity 上 5 个
 * {@code @TableField(exist = false)} view 字段（courseName/teacherName/className/roomName/timeLabel）
 * 删除、改由 SchedulePlanItemVo 承载后，getPlanItems 下发 JSON 与历史逐字段一致。
 *
 * 本批覆盖：
 * - 24 字段集（19 持久化〔含 @TableLogic deleted 恒 0、createdAt/@TableField("updated_at") updatedAt、
 *   BigDecimal score〕+ 5 view）；
 * - view 字段填充态 / 关联缺失 null 态；
 * - deleted 恒 0（严格逐字段）。
 *
 * 走真实 wire 路径（writeValueAsString + readTree），findAndRegisterModules 支持 LocalDateTime。
 */
class M16PlanItemVoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "id", "planId", "semesterId", "teachingTaskId", "teacherId", "classId",
            "courseId", "classroomId", "weekday", "startPeriod", "endPeriod", "weekType",
            "score", "conflictFlag", "conflictReason", "sourceType", "createdAt", "updatedAt",
            "deleted", "courseName", "teacherName", "className", "roomName", "timeLabel");

    @Test
    void planItemVoKeepsAllTwentyFourFieldsWithRelations() throws Exception {
        JsonNode json = toJson(new SchedulePlanItemVo(
                1L, 10L, 2L, 30L, 40L, 50L, 60L, 70L,
                1, 1, 2, "all",
                new BigDecimal("95.5"), 0, null, "AUTO",
                LocalDateTime.of(2026, 6, 3, 10, 0, 0), LocalDateTime.of(2026, 6, 3, 10, 0, 0), 0,
                "高等数学", "张老师", "计科2401", "A101", "周1 第1-2节"));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals(24, fieldNames(json).size());
        assertEquals(1L, json.get("id").asLong());
        assertEquals(10L, json.get("planId").asLong());
        assertEquals(30L, json.get("teachingTaskId").asLong());
        assertEquals(0, new BigDecimal("95.5").compareTo(json.get("score").decimalValue()));
        assertEquals(0, json.get("conflictFlag").asInt());
        assertEquals(0, json.get("deleted").asInt());
        assertEquals("高等数学", json.get("courseName").asText());
        assertEquals("张老师", json.get("teacherName").asText());
        assertEquals("计科2401", json.get("className").asText());
        assertEquals("A101", json.get("roomName").asText());
        assertEquals("周1 第1-2节", json.get("timeLabel").asText());
    }

    @Test
    void planItemVoKeepsNullRelationFields() throws Exception {
        SchedulePlanItemVo vo = new SchedulePlanItemVo();
        vo.setId(2L);
        vo.setPlanId(11L);
        vo.setTeachingTaskId(31L);
        vo.setDeleted(0);
        JsonNode json = toJson(vo);

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertTrue(json.get("courseName").isNull());
        assertTrue(json.get("teacherName").isNull());
        assertTrue(json.get("className").isNull());
        assertTrue(json.get("roomName").isNull());
        assertTrue(json.get("timeLabel").isNull());
        assertTrue(json.get("createdAt").isNull());
        assertTrue(json.get("updatedAt").isNull());
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
