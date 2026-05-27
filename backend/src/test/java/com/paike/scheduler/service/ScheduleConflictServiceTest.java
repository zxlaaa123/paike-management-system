package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduleConflictServiceTest {

    private ScheduleMapper scheduleMapper;
    private TeachingTaskMapper teachingTaskMapper;
    private TeacherMapper teacherMapper;
    private ClassInfoMapper classInfoMapper;
    private ClassroomMapper classroomMapper;
    private TimeSlotMapper timeSlotMapper;
    private TeacherUnavailableTimeService unavailableTimeService;
    private ScheduleConflictService service;
    private Classroom classroom;
    private ClassInfo classInfo;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Schedule.class);
        scheduleMapper = mock(ScheduleMapper.class);
        teachingTaskMapper = mock(TeachingTaskMapper.class);
        teacherMapper = mock(TeacherMapper.class);
        classInfoMapper = mock(ClassInfoMapper.class);
        classroomMapper = mock(ClassroomMapper.class);
        timeSlotMapper = mock(TimeSlotMapper.class);
        unavailableTimeService = mock(TeacherUnavailableTimeService.class);
        service = new ScheduleConflictService(
                scheduleMapper,
                teachingTaskMapper,
                teacherMapper,
                classInfoMapper,
                classroomMapper,
                mock(CourseMapper.class),
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

    private void seedActiveResources() {
        TeachingTask task = new TeachingTask();
        task.setId(1L);
        task.setDeleted(0);
        task.setTeacherId(10L);
        task.setClassId(20L);
        task.setCourseId(30L);
        task.setSemesterId(40L);
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setId(2L);
        timeSlot.setTimeLabel("周一第一节");
        Teacher teacher = new Teacher();
        teacher.setStatus(1);
        teacher.setName("张");
        classInfo = new ClassInfo();
        classInfo.setStatus(1);
        classInfo.setStudentCount(40);
        classInfo.setClassName("软件一班");
        classroom = new Classroom();
        classroom.setId(3L);
        classroom.setDeleted(0);
        classroom.setStatus(1);
        classroom.setCapacity(50);
        classroom.setRoomName("A101");

        when(teachingTaskMapper.selectById(1L)).thenReturn(task);
        when(timeSlotMapper.selectById(2L)).thenReturn(timeSlot);
        when(classroomMapper.selectById(3L)).thenReturn(classroom);
        when(teacherMapper.selectById(10L)).thenReturn(teacher);
        when(classInfoMapper.selectById(20L)).thenReturn(classInfo);
        when(unavailableTimeService.isUnavailable(10L, 2L)).thenReturn(false);
    }
}
