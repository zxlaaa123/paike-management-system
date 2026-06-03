package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.ScheduleUnassignedTaskVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-16 第2批序列化守卫：锁定 ScheduleUnassignedTask Entity 上 3 个
 * {@code @TableField(exist = false)} view 字段（courseName/teacherName/className）删除、改由
 * ScheduleUnassignedTaskVo 承载后，下发 JSON 字段名/类型与历史一致（前端三字段可选、零改动）。
 *
 * 本批关键差异：该 VO 既下发前端，也被 V4ScheduleRiskService 内部读取，故覆盖
 * 「三字段填充态」与「三字段为 null（关联缺失）态」两种。
 *
 * 走真实 wire 路径（writeValueAsString + readTree），findAndRegisterModules 支持 LocalDateTime。
 */
class M16UnassignedTaskVoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "id", "planId", "semesterId", "teachingTaskId", "reasonCode", "reasonMessage",
            "suggestion", "createdAt", "courseName", "teacherName", "className");

    @Test
    void unassignedTaskVoKeepsAllElevenFieldsWithRelations() throws Exception {
        JsonNode json = toJson(new ScheduleUnassignedTaskVo(
                1L, 34L, 2L, 88L, "NO_AVAILABLE_CLASSROOM", "无可用教室",
                "建议优先处理未排任务", LocalDateTime.of(2026, 6, 3, 10, 0, 0),
                "数据结构", "张老师", "计科2101"));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals(11, fieldNames(json).size());
        assertEquals(1L, json.get("id").asLong());
        assertEquals(34L, json.get("planId").asLong());
        assertEquals(2L, json.get("semesterId").asLong());
        assertEquals(88L, json.get("teachingTaskId").asLong());
        assertEquals("NO_AVAILABLE_CLASSROOM", json.get("reasonCode").asText());
        assertEquals("无可用教室", json.get("reasonMessage").asText());
        assertEquals("建议优先处理未排任务", json.get("suggestion").asText());
        assertEquals("数据结构", json.get("courseName").asText());
        assertEquals("张老师", json.get("teacherName").asText());
        assertEquals("计科2101", json.get("className").asText());
    }

    @Test
    void unassignedTaskVoKeepsNullRelationFields() throws Exception {
        // 关联缺失（task/course/teacher/class 任一查不到）时三字段为 null：普通 POJO 不省略键
        ScheduleUnassignedTaskVo vo = new ScheduleUnassignedTaskVo();
        vo.setId(2L);
        vo.setPlanId(34L);
        vo.setTeachingTaskId(99L);
        vo.setReasonCode("UNKNOWN_REASON");
        JsonNode json = toJson(vo);

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertTrue(json.has("courseName"));
        assertTrue(json.get("courseName").isNull());
        assertTrue(json.get("teacherName").isNull());
        assertTrue(json.get("className").isNull());
        assertTrue(json.get("suggestion").isNull());
        assertTrue(json.get("createdAt").isNull());
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
