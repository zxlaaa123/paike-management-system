package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.paike.scheduler.entity.Semester;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void delete_recordsSuccessAuditAfterScheduleDeleted() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        ScheduleService service = newService(scheduleMapper, teachingTaskMapper, mock(TimeSlotMapper.class),
                mock(ClassroomMapper.class), mock(ScheduleConflictService.class), auditLogService);
        Schedule schedule = new Schedule();
        schedule.setId(99L);
        schedule.setSemesterId(3L);
        schedule.setPlanId(7L);
        schedule.setDeleted(0);
        when(scheduleMapper.selectById(99L)).thenReturn(schedule);

        service.delete(99L);

        verify(auditLogService).recordSuccess(
                eq(SystemAuditLogService.ACTION_DELETE_SCHEDULE),
                eq(SystemAuditLogService.TARGET_SCHEDULE),
                eq(99L),
                eq(3L),
                eq(7L),
                any());
    }

    @Test
    void delete_recordsFailureAuditWhenScheduleMissing() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        ScheduleService service = newService(scheduleMapper, teachingTaskMapper, mock(TimeSlotMapper.class),
                mock(ClassroomMapper.class), mock(ScheduleConflictService.class), auditLogService);

        assertThrows(RuntimeException.class, () -> service.delete(99L));

        verify(auditLogService).recordFailure(
                eq(SystemAuditLogService.ACTION_DELETE_SCHEDULE),
                eq(SystemAuditLogService.TARGET_SCHEDULE),
                eq(99L),
                eq(null),
                eq(null),
                eq(SystemAuditLogService.ERROR_BUSINESS),
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
                eq(SystemAuditLogService.ERROR_BUSINESS),
                any());
    }

    @Test
    void listByClass_usesExplicitSemesterForTaskAndScheduleQueries() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        ScheduleService service = newService(scheduleMapper, teachingTaskMapper, mock(TimeSlotMapper.class),
                mock(ClassroomMapper.class), mock(ScheduleConflictService.class), mock(SystemAuditLogService.class));
        TeachingTask task = new TeachingTask();
        task.setId(10L);
        task.setClassId(20L);
        task.setSemesterId(3L);
        when(teachingTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(scheduleMapper.selectList(any())).thenReturn(List.of());

        service.listByClass(20L, 3L);

        ArgumentCaptor<LambdaQueryWrapper<TeachingTask>> taskCaptor = teachingTaskWrapperCaptor();
        ArgumentCaptor<LambdaQueryWrapper<Schedule>> scheduleCaptor = scheduleWrapperCaptor();
        verify(teachingTaskMapper).selectList(taskCaptor.capture());
        verify(scheduleMapper).selectList(scheduleCaptor.capture());
        assertWrapperContains(taskCaptor.getValue(), TeachingTask.class, "class_id", 20L);
        assertWrapperContains(taskCaptor.getValue(), TeachingTask.class, "semester_id", 3L);
        assertWrapperContains(scheduleCaptor.getValue(), Schedule.class, "semester_id", 3L);
        assertWrapperContains(scheduleCaptor.getValue(), Schedule.class, "teaching_task_id", 10L);
    }

    @Test
    void listByClassroom_usesCurrentSemesterWhenRequestDoesNotProvideSemester() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        SemesterService semesterService = mock(SemesterService.class);
        Semester semester = new Semester();
        semester.setId(4L);
        when(semesterService.getCurrentSemester()).thenReturn(semester);
        ScheduleService service = newService(scheduleMapper, mock(TeachingTaskMapper.class), mock(TimeSlotMapper.class),
                mock(ClassroomMapper.class), mock(ScheduleConflictService.class), semesterService, mock(SystemAuditLogService.class));
        when(scheduleMapper.selectList(any())).thenReturn(List.of());

        service.listByClassroom(30L, null);

        ArgumentCaptor<LambdaQueryWrapper<Schedule>> scheduleCaptor = scheduleWrapperCaptor();
        verify(scheduleMapper).selectList(scheduleCaptor.capture());
        assertWrapperContains(scheduleCaptor.getValue(), Schedule.class, "classroom_id", 30L);
        assertWrapperContains(scheduleCaptor.getValue(), Schedule.class, "semester_id", 4L);
    }

    private ScheduleService newService(
            ScheduleMapper scheduleMapper,
            TeachingTaskMapper teachingTaskMapper,
            TimeSlotMapper timeSlotMapper,
            ClassroomMapper classroomMapper,
            ScheduleConflictService conflictService,
            SystemAuditLogService auditLogService
    ) {
        return newService(scheduleMapper, teachingTaskMapper, timeSlotMapper, classroomMapper, conflictService,
                mock(SemesterService.class), auditLogService);
    }

    private ScheduleService newService(
            ScheduleMapper scheduleMapper,
            TeachingTaskMapper teachingTaskMapper,
            TimeSlotMapper timeSlotMapper,
            ClassroomMapper classroomMapper,
            ScheduleConflictService conflictService,
            SemesterService semesterService,
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
                semesterService,
                auditLogService);
    }

    private void assertWrapperContains(LambdaQueryWrapper<?> wrapper, Class<?> entityType, String column, Object value) {
        ensureTableInfo(entityType);
        assertTrue(wrapper.getSqlSegment().contains(column), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(value),
                column + " value missing from " + wrapper.getParamNameValuePairs());
    }

    private void ensureTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<TeachingTask>> teachingTaskWrapperCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<Schedule>> scheduleWrapperCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }
}
