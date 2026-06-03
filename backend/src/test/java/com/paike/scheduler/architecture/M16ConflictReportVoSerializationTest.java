package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.service.vo.ScheduleConflictReportVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-16 第1批序列化守卫：锁定 ScheduleConflictReport Entity 上的
 * {@code @TableField(exist = false) timeSlotName} view 字段删除、改由 ScheduleConflictReportVo
 * 承载后，list 端点的 JSON 字段名/类型与历史输出逐一一致（前端 timeSlotName?:string 零改动）。
 *
 * 重点覆盖：
 * - 13 字段集（12 持久化列 + timeSlotName），顺序无关、名称逐字段比对；
 * - timeSlotName 三态：时段 label / "全周"（TASK_NOT_FULLY_SCHEDULED）/ "-"（兜底）；
 * - 普通 POJO 保留 null 序列化（可选字段为 null 时键仍存在，对齐前端可选声明）。
 *
 * 与 M-14 各阶段一致：走真实 wire 路径（writeValueAsString + readTree），
 * findAndRegisterModules 支持 LocalDateTime。
 */
class M16ConflictReportVoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "id", "semesterId", "reportNo", "conflictType", "objectType", "objectId",
            "objectName", "timeSlotId", "relatedScheduleIds", "description", "suggestion",
            "createTime", "timeSlotName");

    @Test
    void conflictReportVoKeepsAllThirteenFieldsWithTimeSlotLabel() throws Exception {
        // timeSlotId 有值 -> timeSlotName 为时段 label
        JsonNode json = toJson(new ScheduleConflictReportVo(
                1L, 2L, "CR20260603100000", "TEACHER_CONFLICT", "TEACHER", 10L, "张老师",
                901L, "11,12", "张老师在周一 第1-2节存在2条课程安排", "建议调整其中一门课程",
                LocalDateTime.of(2026, 6, 3, 10, 0, 0), "周一 第1-2节"));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals(13, fieldNames(json).size());
        assertEquals(1L, json.get("id").asLong());
        assertEquals(2L, json.get("semesterId").asLong());
        assertEquals("CR20260603100000", json.get("reportNo").asText());
        assertEquals("TEACHER_CONFLICT", json.get("conflictType").asText());
        assertEquals("TEACHER", json.get("objectType").asText());
        assertEquals(10L, json.get("objectId").asLong());
        assertEquals("张老师", json.get("objectName").asText());
        assertEquals(901L, json.get("timeSlotId").asLong());
        assertEquals("11,12", json.get("relatedScheduleIds").asText());
        assertEquals("周一 第1-2节", json.get("timeSlotName").asText());
    }

    @Test
    void conflictReportVoTimeSlotNameWholeWeekForUnfinishedTask() throws Exception {
        // TASK_NOT_FULLY_SCHEDULED + timeSlotId 为 null -> timeSlotName 为 "全周"
        JsonNode json = toJson(new ScheduleConflictReportVo(
                5L, 2L, "CR20260603100000", "TASK_NOT_FULLY_SCHEDULED", "TASK", 88L,
                "数据结构 / 张老师 / 计科2101", null, "", "每周需安排2个大节，仅排了1个",
                "建议优先补排该教学任务", LocalDateTime.of(2026, 6, 3, 10, 0, 0), "全周"));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals("全周", json.get("timeSlotName").asText());
        assertTrue(json.get("timeSlotId").isNull());
    }

    @Test
    void conflictReportVoTimeSlotNameDashWhenUnresolved() throws Exception {
        // 非任务类、timeSlotId 为 null、relatedSchedules 取不到 weekday -> timeSlotName 为 "-"
        JsonNode json = toJson(new ScheduleConflictReportVo(
                9L, 2L, "CR20260603100000", "TEACHER_DAILY_LIMIT", "TEACHER", 10L, "张老师",
                null, "11,12,13", "张老师在某日共安排了多个大节", "建议将部分课程调整到其他工作日",
                LocalDateTime.of(2026, 6, 3, 10, 0, 0), "-"));

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertEquals("-", json.get("timeSlotName").asText());
    }

    @Test
    void conflictReportVoKeepsNullOptionalFields() throws Exception {
        // 普通 POJO 不省略 null：可选字段为 null 时键仍在（对齐前端 timeSlotName?:string 等可选声明）
        ScheduleConflictReportVo vo = new ScheduleConflictReportVo();
        vo.setId(1L);
        vo.setReportNo("CR20260603100000");
        vo.setConflictType("TEACHER_CONFLICT");
        JsonNode json = toJson(vo);

        assertEquals(EXPECTED_FIELDS, fieldNames(json));
        assertTrue(json.has("timeSlotName"));
        assertTrue(json.get("timeSlotName").isNull());
        assertTrue(json.get("objectName").isNull());
        assertTrue(json.get("timeSlotId").isNull());
        assertTrue(json.get("createTime").isNull());
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
