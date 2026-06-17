package com.paike.scheduler.service;

import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.mapper.ScheduleRuleWeightMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleRuleWeightServiceTest {

    private ScheduleRuleWeightMapper ruleWeightMapper;
    private ScheduleRuleWeightService service;

    @BeforeEach
    void setUp() {
        ruleWeightMapper = mock(ScheduleRuleWeightMapper.class);
        service = new ScheduleRuleWeightService(ruleWeightMapper);
    }

    @Test
    void initDefaultRules_comprehensiveDoesNotEnableMorningTheoryPriority() {
        when(ruleWeightMapper.selectCount(any())).thenReturn(0L);

        service.initDefaultRules(1L, "COMPREHENSIVE");

        List<ScheduleRuleWeight> inserted = captureInsertedRules(11);
        Map<String, ScheduleRuleWeight> byCode = byRuleCode(inserted);

        assertFalse(byCode.containsKey("MORNING_THEORY_PRIORITY"));
        assertEquals(new BigDecimal("20"), byCode.get("CLASSROOM_UTILIZATION").getWeight());
    }

    @Test
    void initDefaultRules_classBalanceEnablesMorningTheoryPriority() {
        when(ruleWeightMapper.selectCount(any())).thenReturn(0L);

        service.initDefaultRules(1L, "CLASS_BALANCE");

        List<ScheduleRuleWeight> inserted = captureInsertedRules(8);
        Map<String, ScheduleRuleWeight> byCode = byRuleCode(inserted);

        assertTrue(byCode.containsKey("MORNING_THEORY_PRIORITY"));
        assertEquals(new BigDecimal("25"), byCode.get("MORNING_THEORY_PRIORITY").getWeight());
        assertEquals("SOFT", byCode.get("MORNING_THEORY_PRIORITY").getRuleType());
        // CLASS_GAP_PENALTY 是 CLASS_BALANCE 策略的学生中心维度，默认权重 30
        assertTrue(byCode.containsKey("CLASS_GAP_PENALTY"));
        assertEquals(new BigDecimal("30"), byCode.get("CLASS_GAP_PENALTY").getWeight());
        assertEquals("SOFT", byCode.get("CLASS_GAP_PENALTY").getRuleType());
    }

    private List<ScheduleRuleWeight> captureInsertedRules(int count) {
        ArgumentCaptor<ScheduleRuleWeight> captor = ArgumentCaptor.forClass(ScheduleRuleWeight.class);
        verify(ruleWeightMapper, times(count)).insert(captor.capture());
        return captor.getAllValues();
    }

    private Map<String, ScheduleRuleWeight> byRuleCode(List<ScheduleRuleWeight> rules) {
        return rules.stream().collect(Collectors.toMap(ScheduleRuleWeight::getRuleCode, rule -> rule));
    }
}
