package com.paike.scheduler.service;

import com.paike.scheduler.entity.ScheduleRuleConfig;
import com.paike.scheduler.mapper.ScheduleRuleConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleRuleServiceTest {

    private ScheduleRuleConfigMapper ruleConfigMapper;
    private ScheduleRuleService service;

    @BeforeEach
    void setUp() {
        ruleConfigMapper = mock(ScheduleRuleConfigMapper.class);
        service = new ScheduleRuleService(ruleConfigMapper);
    }

    @Test
    void updateRulesClearsCachedIntValue() {
        ScheduleRuleConfig cachedRule = rule("TEACHER_MAX_DAILY_SLOTS", "3", 1);
        ScheduleRuleConfig existingRule = rule("TEACHER_MAX_DAILY_SLOTS", "3", 1);
        ScheduleRuleConfig updatedRule = rule("TEACHER_MAX_DAILY_SLOTS", "5", 1);
        when(ruleConfigMapper.selectOne(any())).thenReturn(cachedRule, existingRule, updatedRule);

        assertEquals(3, service.getIntValue("TEACHER_MAX_DAILY_SLOTS"));
        assertEquals(3, service.getIntValue("TEACHER_MAX_DAILY_SLOTS"));

        ScheduleRuleConfig update = rule("TEACHER_MAX_DAILY_SLOTS", "5", 1);
        service.updateRules(List.of(update));

        assertEquals(5, service.getIntValue("TEACHER_MAX_DAILY_SLOTS"));
        verify(ruleConfigMapper, times(3)).selectOne(any());
        verify(ruleConfigMapper).updateById(existingRule);
    }

    @Test
    void resetToDefaultClearsCachedBoolValue() {
        when(ruleConfigMapper.selectOne(any())).thenReturn(
                rule("PRIORITIZE_MORNING", "false", 1),
                rule("TEACHER_MAX_DAILY_SLOTS", "9", 1),
                rule("CLASS_MAX_DAILY_SLOTS", "9", 1),
                rule("PRIORITIZE_MORNING", "false", 1),
                rule("AVOID_FRIDAY_AFTERNOON", "false", 1),
                rule("ALLOW_SAME_COURSE_SAME_DAY", "true", 1),
                rule("PRIORITIZE_MORNING", "true", 1)
        );

        assertEquals(false, service.getBoolValue("PRIORITIZE_MORNING"));

        service.resetToDefault();

        assertTrue(service.getBoolValue("PRIORITIZE_MORNING"));
        verify(ruleConfigMapper, times(7)).selectOne(any());
    }

    private ScheduleRuleConfig rule(String key, String value, int enabled) {
        ScheduleRuleConfig rule = new ScheduleRuleConfig();
        rule.setRuleKey(key);
        rule.setRuleValue(value);
        rule.setEnabled(enabled);
        return rule;
    }
}
