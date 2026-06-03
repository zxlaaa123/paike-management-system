package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.TeacherUnavailableTimeVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-16 第3批序列化守卫：锁定 TeacherUnavailableTime Entity 上 5 个
 * {@code @TableField(exist = false)} view 字段（teacherName/department/timeSlotName/dayOfWeek/periodNo）
 * 删除、改由 TeacherUnavailableTimeVo 承载后，list/create/update 三端点下发 JSON 与历史逐字段一致。
 *
 * 本批覆盖：
 * - 14 字段集（9 持久化〔含 @TableLogic deleted、createTime、updateTime〕+ 5 view）；
 * - view 字段填充态 / 关联缺失 null 态；
 * - deleted 恒 0（严格逐字段，镜像 Entity 当前序列化）。
 *
 * 走真实 wire 路径（writeValueAsString + readTree），findAndRegisterModules 支持 LocalDateTime。
 */
class M16UnavailableTimeVoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "id", "teacherId", "timeSlotId", "reason", "status", "remark", "deleted",
            "createTime", "updateTime", "teacherName", "department", "timeSlotName",
            "dayOfWeek", "periodNo");

    @Test
    void unavailableTimeVoKeepsAllFourteenFieldsWithRelations() throws Exception {
        JsonNode json = toJson(new TeacherUnavailableTimeVo(
                1L, 10L, 2L, "会议", 1, "每周一例会", 0,
                LocalDateTime.of(2026, 6, 3, 10, 0, 0), LocalDateTime.of(2026, 6, 3, 10, 0, 0),
                "张老师", "计算机系", "周一第1大节", 1, 1));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals(14, fieldNames(json).size());
        assertEquals(1L, json.get("id").asLong());
        assertEquals(10L, json.get("teacherId").asLong());
        assertEquals(2L, json.get("timeSlotId").asLong());
        assertEquals("会议", json.get("reason").asText());
        assertEquals(1, json.get("status").asInt());
        assertEquals("每周一例会", json.get("remark").asText());
        assertEquals(0, json.get("deleted").asInt());
        assertEquals("张老师", json.get("teacherName").asText());
        assertEquals("计算机系", json.get("department").asText());
        assertEquals("周一第1大节", json.get("timeSlotName").asText());
        assertEquals(1, json.get("dayOfWeek").asInt());
        assertEquals(1, json.get("periodNo").asInt());
    }

    @Test
    void unavailableTimeVoKeepsNullRelationFields() throws Exception {
        // 关联缺失（teacher/timeSlot 查不到）时 5 个 view 字段为 null：普通 POJO 不省略键
        TeacherUnavailableTimeVo vo = new TeacherUnavailableTimeVo();
        vo.setId(2L);
        vo.setTeacherId(99L);
        vo.setTimeSlotId(88L);
        vo.setStatus(1);
        vo.setDeleted(0);
        JsonNode json = toJson(vo);

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertTrue(json.get("teacherName").isNull());
        assertTrue(json.get("department").isNull());
        assertTrue(json.get("timeSlotName").isNull());
        assertTrue(json.get("dayOfWeek").isNull());
        assertTrue(json.get("periodNo").isNull());
        assertTrue(json.get("createTime").isNull());
        assertTrue(json.get("updateTime").isNull());
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
