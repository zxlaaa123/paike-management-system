package com.paike.scheduler.service;

import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.mapper.AutoScheduleBatchMapper;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleServiceAuditTest {

    @Test
    void create_recordsSuccessAuditAfterManualScheduleCreated() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
        ScheduleConflictService conflictService = mock(ScheduleConflictService.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        ScheduleService service = newService(scheduleMapper, teachingTaskMapper, timeSlotMapper, classroomMapper, conflictService, auditLogService);
        TeachingTask task = new TeachingTask();
        task.setId(10L);
        task.setSemesterId(3L);
        when(teachingTaskMapper.selectById(10L)).thenReturn(task);
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of());
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setId(20L);
        when(timeSlotMapper.selectBatchIds(any())).thenReturn(List.of(timeSlot));
        Classroom classroom = new Classroom();
        classroom.setId(30L);
        when(classroomMapper.selectBatchIds(any())).thenReturn(List.of(classroom));
        doAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            schedule.setId(99L);
            return 1;
        }).when(scheduleMapper).insert(any(Schedule.class));

        service.create(10L, 20L, 30L);

        verify(auditLogService).recordSuccess(
                eq(SystemAuditLogService.ACTION_CREATE_SCHEDULE),
                eq(SystemAuditLogService.TARGET_SCHEDULE),
                eq(99L),
                eq(3L),
                eq(null),
                any());
    }

    @Test
    void create_recordsFailureAuditWhenConflictRejected() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        ScheduleConflictService conflictService = mock(ScheduleConflictService.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        ScheduleService service = newService(scheduleMapper, teachingTaskMapper, mock(TimeSlotMapper.class), mock(ClassroomMapper.class), conflictService, auditLogService);
        TeachingTask task = new TeachingTask();
        task.setId(10L);
        task.setSemesterId(3L);
        when(teachingTaskMapper.selectById(10L)).thenReturn(task);
        when(conflictService.checkConflict(10L, 20L, 30L, null)).thenReturn("ROOM_CONFLICT:教室已被占用");

        try {
            service.create(10L, 20L, 30L);
        } catch (RuntimeException ignored) {
            // expected
        }

        verify(auditLogService).recordFailure(
                eq(SystemAuditLogService.ACTION_CREATE_SCHEDULE),
                eq(SystemAuditLogService.TARGET_SCHEDULE),
                eq(null),
                eq(3L),
                eq(null),
                eq("BUSINESS_ERROR"),
                any());
    }

    private ScheduleService newService(
            ScheduleMapper scheduleMapper,
            TeachingTaskMapper teachingTaskMapper,
            TimeSlotMapper timeSlotMapper,
            ClassroomMapper classroomMapper,
            ScheduleConflictService conflictService,
            SystemAuditLogService auditLogService
    ) {
        return new ScheduleService(
                scheduleMapper,
                teachingTaskMapper,
                timeSlotMapper,
                classroomMapper,
                mock(CourseMapper.class),
                mock(TeacherMapper.class),
                mock(ClassInfoMapper.class),
                conflictService,
                mock(ScheduleLockGuardService.class),
                mock(AutoScheduleBatchMapper.class),
                mock(SemesterService.class),
                auditLogService);
    }
}
