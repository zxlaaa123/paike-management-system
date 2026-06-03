package com.paike.scheduler.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.auth.vo.LogoutResultVo;
import com.paike.scheduler.controller.vo.ConflictCheckResultVo;
import com.paike.scheduler.controller.vo.HealthInfoVo;
import com.paike.scheduler.controller.vo.RescoreResultVo;
import com.paike.scheduler.controller.vo.ScoreSummaryVo;
import com.paike.scheduler.service.vo.UnassignedSummaryVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-14 阶段1 序列化守卫：锁定 6 个低风险端点把 Map 弱类型替换为 VO 后，
 * JSON 字段名/值与历史输出逐一一致（纯机械替换、契约不变）。
 *
 * 对应 gpt-5.5 评语：「先用序列化测试锁定现有 JSON 输出，再做机械替换」——本测试即该锁。
 * 每个用例的期望字段集 == 替换前 Map 的键集；标量值 == 替换前 put 的值。
 *
 * 实现说明：用 {@code writeValueAsString + readTree} 走真实 wire 路径，
 * 而非 {@code valueToTree}（后者的 TokenBuffer 会对 BigDecimal 调 stripTrailingZeros，
 * 把 90 写成 9E+1，与 HTTP 实际输出不符）。BigDecimal 一律用数值比较，忽略 scale 差异。
 */
class M14Phase1VoSerializationTest {

    /** 注册 JavaTimeModule，使 HealthInfoVo.time(LocalDateTime) 可序列化（与运行时 ObjectMapper 行为一致）。 */
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void healthInfoVoMatchesLegacyJson() throws Exception {
        // 历史：LinkedHashMap{status:"UP", service:"scheduler-backend", time:LocalDateTime.now()}
        JsonNode json = toJson(new HealthInfoVo("UP", "scheduler-backend", LocalDateTime.of(2026, 6, 3, 10, 0, 0)));
        assertEquals(Set.of("status", "service", "time"), fieldNames(json));
        assertEquals("UP", json.get("status").asText());
        assertEquals("scheduler-backend", json.get("service").asText());
        assertFalse(json.get("time").isNull());
    }

    @Test
    void logoutResultVoMatchesLegacyJson() throws Exception {
        // 历史：Map.of("success", true)
        JsonNode json = toJson(new LogoutResultVo(true));
        assertEquals(Set.of("success"), fieldNames(json));
        assertTrue(json.get("success").asBoolean());
    }

    @Test
    void conflictCheckResultVoMatchesLegacyJson() throws Exception {
        // 历史（有冲突）：Map.of("hasConflict", true, "message", conflict)
        JsonNode withConflict = toJson(new ConflictCheckResultVo(true, "教师时间冲突"));
        assertEquals(Set.of("hasConflict", "message"), fieldNames(withConflict));
        assertTrue(withConflict.get("hasConflict").asBoolean());
        assertEquals("教师时间冲突", withConflict.get("message").asText());

        // 历史（无冲突）：Map.of("hasConflict", false, "message", "") —— message 为空串非 null
        JsonNode noConflict = toJson(new ConflictCheckResultVo(false, ""));
        assertEquals(Set.of("hasConflict", "message"), fieldNames(noConflict));
        assertFalse(noConflict.get("hasConflict").asBoolean());
        assertEquals("", noConflict.get("message").asText());
    }

    @Test
    void scoreSummaryVoMatchesLegacyJson() throws Exception {
        // 历史：Map.of(planId, totalScore, hardViolationCount, softViolationCount, scoreLevel) —— 5 字段，不含 conflictCount
        JsonNode json = toJson(new ScoreSummaryVo(7L, new BigDecimal("85.5"), 1, 2, "良好"));
        assertEquals(
                Set.of("planId", "totalScore", "hardViolationCount", "softViolationCount", "scoreLevel"),
                fieldNames(json));
        assertEquals(7L, json.get("planId").asLong());
        assertEquals(0, json.get("totalScore").decimalValue().compareTo(new BigDecimal("85.5")));
        assertEquals(1, json.get("hardViolationCount").asInt());
        assertEquals(2, json.get("softViolationCount").asInt());
        assertEquals("良好", json.get("scoreLevel").asText());
    }

    @Test
    void rescoreResultVoMatchesLegacyJson() throws Exception {
        // 历史：Map.of(planId, totalScore, conflictCount, scoreLevel)
        JsonNode json = toJson(new RescoreResultVo(7L, new BigDecimal("90"), 0, "优秀"));
        assertEquals(
                Set.of("planId", "totalScore", "conflictCount", "scoreLevel"),
                fieldNames(json));
        assertEquals(7L, json.get("planId").asLong());
        assertEquals(0, json.get("totalScore").decimalValue().compareTo(new BigDecimal("90")));
        assertEquals(0, json.get("conflictCount").asInt());
        assertEquals("优秀", json.get("scoreLevel").asText());
    }

    @Test
    void unassignedSummaryVoMatchesLegacyJson() throws Exception {
        // 历史：LinkedHashMap{reasonCode, reasonName, count(Long)}
        JsonNode json = toJson(new UnassignedSummaryVo("NO_AVAILABLE_CLASSROOM", "无可用教室", 3L));
        assertEquals(Set.of("reasonCode", "reasonName", "count"), fieldNames(json));
        assertEquals("NO_AVAILABLE_CLASSROOM", json.get("reasonCode").asText());
        assertEquals("无可用教室", json.get("reasonName").asText());
        assertEquals(3L, json.get("count").asLong());
    }

    /** 走真实 wire 路径序列化：writeValueAsString 后再 readTree，避免 valueToTree 的 BigDecimal 归一化。 */
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
