package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.UnscheduledTaskVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-16 第5批序列化守卫：锁定 UnscheduledTask Entity 上 4 个
 * {@code @TableField(exist = false)} view 字段（courseName/teacherName/className/batchNo）
 * 删除、改由 UnscheduledTaskVo（Mapper XML SQL 别名直接映射）承载后，list/listByBatch 下发 JSON
 * 与历史逐字段一致。
 *
 * 本批覆盖：
 * - 17 字段集（13 持久化〔无 @TableLogic → 无 deleted，createTime 为 LocalDateTime〕+ 4 view）；
 * - view 字段填充态 / 关联缺失 null 态；
 * - 本 Entity 无 @TableLogic，故不断言 deleted。
 *
 * 走真实 wire 路径（writeValueAsString + readTree），findAndRegisterModules 支持 LocalDateTime。
 */
class M16UnscheduledTaskVoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "id", "batchId", "semesterId", "taskId", "courseId", "teacherId", "classId",
            "requiredSlots", "scheduledSlots", "remainingSlots", "reasonType", "reasonMessage",
            "createTime", "courseName", "teacherName", "className", "batchNo");

    @Test
    void unscheduledTaskVoKeepsAllSixteenFieldsWithRelations() throws Exception {
        JsonNode json = toJson(new UnscheduledTaskVo(
                1L, 10L, 2L, 30L, 40L, 50L, 60L,
                4, 2, 2, "NO_AVAILABLE_CLASSROOM", "无可用教室",
                LocalDateTime.of(2026, 6, 3, 10, 0, 0),
                "高等数学", "张老师", "计科2401", "BATCH-001"));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals(17, fieldNames(json).size());
        assertEquals(1L, json.get("id").asLong());
        assertEquals(10L, json.get("batchId").asLong());
        assertEquals(30L, json.get("taskId").asLong());
        assertEquals(4, json.get("requiredSlots").asInt());
        assertEquals(2, json.get("scheduledSlots").asInt());
        assertEquals(2, json.get("remainingSlots").asInt());
        assertEquals("NO_AVAILABLE_CLASSROOM", json.get("reasonType").asText());
        assertEquals("无可用教室", json.get("reasonMessage").asText());
        assertEquals("高等数学", json.get("courseName").asText());
        assertEquals("张老师", json.get("teacherName").asText());
        assertEquals("计科2401", json.get("className").asText());
        assertEquals("BATCH-001", json.get("batchNo").asText());
    }

    @Test
    void unscheduledTaskVoKeepsNullRelationFields() throws Exception {
        // SQL 别名可能为 null（LEFT JOIN 无匹配行）
        UnscheduledTaskVo vo = new UnscheduledTaskVo();
        vo.setId(2L);
        vo.setBatchId(20L);
        vo.setTaskId(31L);
        vo.setReasonType("CAPACITY");
        JsonNode json = toJson(vo);

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertTrue(json.get("courseName").isNull());
        assertTrue(json.get("teacherName").isNull());
        assertTrue(json.get("className").isNull());
        assertTrue(json.get("batchNo").isNull());
        assertTrue(json.get("createTime").isNull());
        assertTrue(json.get("requiredSlots").isNull());
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
