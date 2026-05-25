package com.paike.scheduler.service.scheduling;

/**
 * 一次自动排课用到的规则阈值。从 ScheduleRuleService 一次性读出来后只读。
 */
public record RuleConfig(
        int teacherMaxDailySlots,
        int classMaxDailySlots,
        boolean allowSameCourseSameDay
) {}
