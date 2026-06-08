package com.paike.scheduler.service;

import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.vo.TeacherWorkloadVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduleStatisticsServiceTest {

    @Test
    void teacherWorkload_calculatesMaxContinuousPeriodsFromPlanItems() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        TeacherMapper teacherMapper = mock(TeacherMapper.class);
        ScheduleStatisticsService service = newService(
                mock(ScheduleMapper.class),
                planItemMapper,
                planMapper,
                teacherMapper,
                mock(TimeSlotMapper.class));

        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        when(planMapper.selectById(10L)).thenReturn(plan);
        when(planItemMapper.selectList(any())).thenReturn(List.of(
                planItem(1L, 1, 1, 2),
                planItem(2L, 1, 3, 4),
                planItem(3L, 1, 5, 6),
                planItem(4L, 1, 9, 10),
                planItem(5L, 2, 1, 2),
                planItem(6L, 2, 5, 6)));
        when(teacherMapper.selectById(1L)).thenReturn(teacher(1L));

        List<TeacherWorkloadVo> result = service.teacherWorkload(1L, 10L);

        assertEquals(1, result.size());
        TeacherWorkloadVo workload = result.get(0);
        assertEquals(12, workload.getTotalPeriods());
        assertEquals(8, workload.getMaxDailyPeriods());
        assertEquals(3, workload.getMaxContinuousPeriods());
    }

    @Test
    void teacherWorkload_calculatesMaxContinuousPeriodsFromFormalScheduleTimeSlots() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        TeacherMapper teacherMapper = mock(TeacherMapper.class);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        ScheduleStatisticsService service = newService(
                scheduleMapper,
                mock(SchedulePlanItemMapper.class),
                mock(SchedulePlanMapper.class),
                teacherMapper,
                timeSlotMapper);

        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(1L, 1L),
                schedule(2L, 2L),
                schedule(3L, 3L),
                schedule(4L, 5L)));
        when(timeSlotMapper.selectBatchIds(any())).thenReturn(List.of(
                timeSlot(1L, 1, 1),
                timeSlot(2L, 1, 2),
                timeSlot(3L, 1, 3),
                timeSlot(5L, 1, 5)));
        when(teacherMapper.selectById(1L)).thenReturn(teacher(1L));

        List<TeacherWorkloadVo> result = service.teacherWorkload(1L, null);

        assertEquals(1, result.size());
        TeacherWorkloadVo workload = result.get(0);
        assertEquals(8, workload.getTotalPeriods());
        assertEquals(8, workload.getMaxDailyPeriods());
        assertEquals(3, workload.getMaxContinuousPeriods());
    }

    private ScheduleStatisticsService newService(
            ScheduleMapper scheduleMapper,
            SchedulePlanItemMapper planItemMapper,
            SchedulePlanMapper planMapper,
            TeacherMapper teacherMapper,
            TimeSlotMapper timeSlotMapper
    ) {
        return new ScheduleStatisticsService(
                scheduleMapper,
                planItemMapper,
                planMapper,
                mock(ClassroomMapper.class),
                teacherMapper,
                mock(ClassInfoMapper.class),
                timeSlotMapper,
                mock(ScheduleThresholdProperties.class));
    }

    private SchedulePlanItem planItem(Long id, Integer weekday, Integer startPeriod, Integer endPeriod) {
        SchedulePlanItem item = new SchedulePlanItem();
        item.setId(id);
        item.setTeacherId(1L);
        item.setCourseId(id);
        item.setClassId(id);
        item.setWeekday(weekday);
        item.setStartPeriod(startPeriod);
        item.setEndPeriod(endPeriod);
        return item;
    }

    private Schedule schedule(Long id, Long timeSlotId) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setTeacherId(1L);
        schedule.setCourseId(id);
        schedule.setClassId(id);
        schedule.setTimeSlotId(timeSlotId);
        return schedule;
    }

    private TimeSlot timeSlot(Long id, Integer dayOfWeek, Integer periodNo) {
        TimeSlot slot = new TimeSlot();
        slot.setId(id);
        slot.setDayOfWeek(dayOfWeek);
        slot.setPeriodNo(periodNo);
        return slot;
    }

    private Teacher teacher(Long id) {
        Teacher teacher = new Teacher();
        teacher.setId(id);
        teacher.setName("教师" + id);
        return teacher;
    }
}
