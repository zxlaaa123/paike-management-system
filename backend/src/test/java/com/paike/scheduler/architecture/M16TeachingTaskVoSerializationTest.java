package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.TeachingTaskVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-16 第8批序列化守卫：锁定 TeachingTask Entity 上 8 个 view 字段
 * 删除、改由 TeachingTaskVo 承载后，list/create/update/getById/checkConflict 下发 JSON 一致。
 *
 * 20 字段集（12 持久化〔含 deleted 恒 0 + createTime/updateTime〕+ 8 view），
 * view 分两路填充：fillTaskRelations（4 字段）+ selectConflictCheckById XML 别名（6 字段含前 3 重叠）。
 *
 * 走真实 wire 路径，findAndRegisterModules 支持 LocalDateTime。
 */
class M16TeachingTaskVoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "id", "semesterId", "courseId", "teacherId", "classId",
            "weeklyHours", "weekType", "startWeek", "endWeek",
            "needContinuous", "status", "remark", "deleted",
            "createTime", "updateTime",
            "courseName", "teacherName", "className", "scheduledSlots",
            "courseType", "teacherStatus", "classStatus", "studentCount");

    @Test
    void teachingTaskVoKeepsAllNineteenFieldsWithRelations() throws Exception {
        JsonNode json = toJson(new TeachingTaskVo(
                1L, 2L, 10L, 20L, 30L,
                4, "ALL", 1, 8, 1, 1, "备注", 0,
                LocalDateTime.of(2026, 6, 3, 10, 0, 0), LocalDateTime.of(2026, 6, 3, 10, 0, 0),
                "高等数学", "张老师", "计科2401", 2,
                "THEORY", 1, 1, 45));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals(23, fieldNames(json).size());
        assertEquals(0, json.get("deleted").asInt());
        assertEquals("ALL", json.get("weekType").asText());
        assertEquals(1, json.get("startWeek").asInt());
        assertEquals(8, json.get("endWeek").asInt());
        assertEquals("高等数学", json.get("courseName").asText());
        assertEquals("张老师", json.get("teacherName").asText());
        assertEquals("计科2401", json.get("className").asText());
        assertEquals(2, json.get("scheduledSlots").asInt());
        assertEquals("THEORY", json.get("courseType").asText());
        assertEquals(1, json.get("teacherStatus").asInt());
        assertEquals(1, json.get("classStatus").asInt());
        assertEquals(45, json.get("studentCount").asInt());
    }

    @Test
    void teachingTaskVoKeepsNullViewFields() throws Exception {
        TeachingTaskVo vo = new TeachingTaskVo();
        vo.setId(2L);
        vo.setSemesterId(3L);
        vo.setDeleted(0);
        JsonNode json = toJson(vo);

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertTrue(json.get("courseName").isNull());
        assertTrue(json.get("teacherName").isNull());
        assertTrue(json.get("scheduledSlots").isNull());
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
