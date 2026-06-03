package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.vo.TeachingTaskVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleConflictServiceTest {

    private ScheduleMapper scheduleMapper;
    private TeachingTaskMapper teachingTaskMapper;
    private ClassroomMapper classroomMapper;
    private TimeSlotMapper timeSlotMapper;
    private TeacherUnavailableTimeService unavailableTimeService;
    private ScheduleConflictService service;
    private Classroom classroom;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Schedule.class);
        scheduleMapper = mock(ScheduleMapper.class);
        teachingTaskMapper = mock(TeachingTaskMapper.class);
        classroomMapper = mock(ClassroomMapper.class);
        timeSlotMapper = mock(TimeSlotMapper.class);
        unavailableTimeService = mock(TeacherUnavailableTimeService.class);
        service = new ScheduleConflictService(
                scheduleMapper,
                teachingTaskMapper,
                classroomMapper,
                timeSlotMapper,
                unavailableTimeService,
                mock(ScheduleRuleService.class));
        seedActiveResources();
    }

    @Test
    void checkConflict_returnsDisabledReasonWhenClassroomStatusMissing() {
        classroom.setStatus(null);

        String reason = service.checkConflict(1L, 2L, 3L, null);

        assertTrue(reason.contains("教室已停用"));
    }

    @Test
    void checkConflict_returnsConfigurationReasonWhenCapacityMissing() {
        classroom.setCapacity(null);

        String reason = service.checkConflict(1L, 2L, 3L, null);

        assertTrue(reason.contains("教室容量未配置"));
    }

    @Test
    void checkConflict_handlesExistingTaskWithMissingTeacherId() {
        Schedule existingSchedule = new Schedule();
        existingSchedule.setTeachingTaskId(9L);
        existingSchedule.setClassroomId(3L);
        TeachingTask existingTask = new TeachingTask();
        existingTask.setId(9L);
        existingTask.setTeacherId(null);
        existingTask.setClassId(99L);
        when(scheduleMapper.selectList(any())).thenReturn(List.of(existingSchedule));
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of(existingTask));

        String reason = service.checkConflict(1L, 2L, 3L, null);

        assertEquals("[ROOM_CONFLICT]排课失败:A101教室在周一第一节已被占用", reason);
    }

    @Test
    void checkConflict_loadsTaskRelatedResourcesWithSingleDetailQuery() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(scheduleMapper.selectCount(any())).thenReturn(0L);

        String reason = service.checkConflict(1L, 2L, 3L, null);

        assertEquals("[TASK_NOT_FULLY_SCHEDULED]排课失败:该教学任务每周课时为0学时,最多排0个大节,当前已排0个大节", reason);
        verify(teachingTaskMapper).selectConflictCheckById(1L);
        verify(teachingTaskMapper, never()).selectById(1L);
    }

    private void seedActiveResources() {
        TeachingTaskVo task = new TeachingTaskVo();
        task.setId(1L);
        task.setDeleted(0);
        task.setTeacherId(10L);
        task.setClassId(20L);
        task.setCourseId(30L);
        task.setSemesterId(40L);
        task.setTeacherName("张");
        task.setTeacherStatus(1);
        task.setClassName("软件一班");
        task.setClassStatus(1);
        task.setStudentCount(40);
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setId(2L);
        timeSlot.setTimeLabel("周一第一节");
        classroom = new Classroom();
        classroom.setId(3L);
        classroom.setDeleted(0);
        classroom.setStatus(1);
        classroom.setCapacity(50);
        classroom.setRoomName("A101");

        when(teachingTaskMapper.selectConflictCheckById(1L)).thenReturn(task);
        when(timeSlotMapper.selectById(2L)).thenReturn(timeSlot);
        when(classroomMapper.selectById(3L)).thenReturn(classroom);
        when(unavailableTimeService.isUnavailable(10L, 2L)).thenReturn(false);
    }
}
