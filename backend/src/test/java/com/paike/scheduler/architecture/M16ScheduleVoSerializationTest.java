package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.ScheduleVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-16 第9批（最后一批）序列化守卫：锁定 Schedule Entity 上 10 个 view 字段
 * 删除、改由 ScheduleVo 承载后，list/getById/create/listByClass/listByTeacher/listByClassroom 下发 JSON 一致。
 *
 * 24 字段集（14 持久化〔含 deleted 恒 0 + createTime/updateTime〕+ 10 view），
 * view 字段由 ScheduleService.fillRelations 从关联表批量查询填充。
 *
 * 走真实 wire 路径，findAndRegisterModules 支持 LocalDateTime。
 */
class M16ScheduleVoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "id", "semesterId", "teachingTaskId", "courseId", "teacherId", "classId",
            "timeSlotId", "weekType", "classroomId", "sourceType", "batchId", "planId",
            "deleted", "createTime", "updateTime",
            "courseName", "teacherName", "className", "timeLabel",
            "dayOfWeek", "periodNo", "roomName", "building",
            "sourceTypeName", "batchNo");

    @Test
    void scheduleVoKeepsAllTwentyFiveFieldsWithRelations() throws Exception {
        JsonNode json = toJson(new ScheduleVo(
                1L, 2L, 10L, 20L, 30L, 40L, 50L, "ODD", 60L, "manual", 70L, 80L,
                0,
                LocalDateTime.of(2026, 6, 3, 10, 0, 0), LocalDateTime.of(2026, 6, 3, 10, 0, 0),
                "高等数学", "张老师", "计科2401", "周一第一节",
                1, 1, "A101", "教学楼A",
                "手动排课", "BATCH-001"));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals(25, fieldNames(json).size());
        assertEquals(0, json.get("deleted").asInt());
        assertEquals("高等数学", json.get("courseName").asText());
        assertEquals("张老师", json.get("teacherName").asText());
        assertEquals("计科2401", json.get("className").asText());
        assertEquals("周一第一节", json.get("timeLabel").asText());
        assertEquals(1, json.get("dayOfWeek").asInt());
        assertEquals(1, json.get("periodNo").asInt());
        assertEquals("A101", json.get("roomName").asText());
        assertEquals("教学楼A", json.get("building").asText());
        assertEquals("手动排课", json.get("sourceTypeName").asText());
        assertEquals("BATCH-001", json.get("batchNo").asText());
    }

    @Test
    void scheduleVoKeepsNullViewFields() throws Exception {
        ScheduleVo vo = new ScheduleVo();
        vo.setId(2L);
        vo.setSemesterId(3L);
        vo.setDeleted(0);
        JsonNode json = toJson(vo);

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertTrue(json.get("courseName").isNull());
        assertTrue(json.get("teacherName").isNull());
        assertTrue(json.get("className").isNull());
        assertTrue(json.get("timeLabel").isNull());
        assertTrue(json.get("dayOfWeek").isNull());
        assertTrue(json.get("periodNo").isNull());
        assertTrue(json.get("roomName").isNull());
        assertTrue(json.get("building").isNull());
        assertTrue(json.get("sourceTypeName").isNull());
        assertTrue(json.get("batchNo").isNull());
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
