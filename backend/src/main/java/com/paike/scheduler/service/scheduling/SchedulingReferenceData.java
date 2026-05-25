package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.TimeSlot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次排课所需的全部"读"型数据。所有字段在构造后不可变，循环中只读。
 * 不包含规则阈值（max daily slots 等），那些由调用方按需 ruleService.getIntValue 取。
 * weightMap 仅 V3 用；AutoScheduleService 传 Map.of()。
 */
public record SchedulingReferenceData(
        List<TimeSlot> sortedTimeSlots,
        Map<Integer, List<Long>> slotIdsByDay,
        List<Classroom> classrooms,
        Set<String> unavailableKeySet,
        Map<Long, Long> unavailableCountByTeacher,
        Map<Long, Course> courseMap,
        Map<Long, ClassInfo> classMap,
        Map<String, BigDecimal> weightMap
) {}
