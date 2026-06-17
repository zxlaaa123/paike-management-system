package com.paike.scheduler.service;

import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V4ScheduleRiskServiceTest {

    @Test
    void buildContextReusesLoadedTimeSlotsForTotalCount() throws IOException {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V4ScheduleRiskService.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("timeSlotMapper.selectCount"));
        assertTrue(source.contains("List<TimeSlot> slots = timeSlotMapper.selectList"));
        assertTrue(source.contains("context.totalTimeSlots = Math.max(slots.size(), 1)"));
    }

    /**
     * V9 阶段 2C T7 核心：同 (teacher,weekday,startPeriod) 但 weekType=ODD+EVEN → 0 TEACHER_CONFLICT（合法共槽）。
     */
    @Test
    void getPlanRisks_oddEvenSharedSlotNotReportedAsTeacherConflict() {
        V4ScheduleRiskService service = newServiceWithItems(List.of(
                riskItem(1L, 1L, 1L, 1L, 1L, "ODD"),
                riskItem(2L, 2L, 1L, 1L, 1L, "EVEN")));

        ScheduleRiskListVo result = service.getPlanRisks(10L, null, null, null);

        long teacherConflicts = result.getRisks().stream()
                .filter(r -> "TEACHER_CONFLICT".equals(r.getRiskType()))
                .count();
        assertEquals(0, teacherConflicts, "ODD+EVEN 共槽不应报 TEACHER_CONFLICT");
    }

    /**
     * V9 阶段 2C：ALL + ODD 同槽 → 1 TEACHER_CONFLICT（ALL 与任意 overlap）。
     */
    @Test
    void getPlanRisks_allOverlapsWithOddReportedAsTeacherConflict() {
        V4ScheduleRiskService service = newServiceWithItems(List.of(
                riskItem(1L, 1L, 1L, 1L, 1L, "ALL"),
                riskItem(2L, 2L, 1L, 1L, 1L, "ODD")));

        ScheduleRiskListVo result = service.getPlanRisks(10L, null, null, null);

        long teacherConflicts = result.getRisks().stream()
                .filter(r -> "TEACHER_CONFLICT".equals(r.getRiskType()))
                .count();
        assertEquals(1, teacherConflicts, "ALL+ODD 共槽应报 1 个 TEACHER_CONFLICT");
    }

    /**
     * 纯 ALL 同槽（回归保护）：两条 ALL 同教师同槽 → 仍报冲突（零回归）。
     */
    @Test
    void getPlanRisks_allAllSharedSlotStillReportedAsConflict() {
        V4ScheduleRiskService service = newServiceWithItems(List.of(
                riskItem(1L, 1L, 1L, 1L, 1L, "ALL"),
                riskItem(2L, 2L, 1L, 1L, 1L, "ALL")));

        ScheduleRiskListVo result = service.getPlanRisks(10L, null, null, null);

        long teacherConflicts = result.getRisks().stream()
                .filter(r -> "TEACHER_CONFLICT".equals(r.getRiskType()))
                .count();
        assertEquals(1, teacherConflicts, "ALL+ALL 共槽应报 1 个 TEACHER_CONFLICT（零回归）");
    }

    private V4ScheduleRiskService newServiceWithItems(List<SchedulePlanItem> items) {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SchedulePlanItemMapper itemMapper = mock(SchedulePlanItemMapper.class);
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        plan.setSemesterId(1L);
        when(planMapper.selectById(10L)).thenReturn(plan);
        when(itemMapper.selectList(any())).thenReturn(items);
        ScheduleThresholdProperties thresholds = mock(ScheduleThresholdProperties.class);
        // 阈值设高，避免触发 overload 误报干扰冲突断言
        when(thresholds.getTeacherOverloadMedium()).thenReturn(999);
        when(thresholds.getTeacherOverloadHigh()).thenReturn(999);
        when(thresholds.getClassDailyOverloadMedium()).thenReturn(999);
        when(thresholds.getClassDailyOverloadHigh()).thenReturn(999);
        when(thresholds.getRoomLowUtilization()).thenReturn(BigDecimal.ZERO);
        when(thresholds.getRoomHighUtilization()).thenReturn(new BigDecimal("999"));
        SchedulePlanExplainService explainService = mock(SchedulePlanExplainService.class);
        when(explainService.listUnassignedTasks(10L)).thenReturn(List.of());
        return new V4ScheduleRiskService(
                planMapper,
                itemMapper,
                mock(TeacherMapper.class),
                mock(ClassInfoMapper.class),
                mock(ClassroomMapper.class),
                mock(CourseMapper.class),
                mock(TeachingTaskMapper.class),
                mock(TimeSlotMapper.class),
                mock(TeacherUnavailableTimeService.class),
                explainService,
                thresholds);
    }

    private SchedulePlanItem riskItem(Long id, Long teachingTaskId, Long teacherId, Long classId,
                                      Long classroomId, String weekType) {
        SchedulePlanItem item = new SchedulePlanItem();
        item.setId(id);
        item.setPlanId(10L);
        item.setSemesterId(1L);
        item.setTeachingTaskId(teachingTaskId);
        item.setTeacherId(teacherId);
        item.setClassId(classId);
        item.setCourseId(1L);
        item.setClassroomId(classroomId);
        item.setWeekday(1);
        item.setStartPeriod(1);
        item.setEndPeriod(2);
        item.setWeekType(weekType);
        return item;
    }
}
