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

    /** V9 单双周：当前 task=ODD，已有 task=EVEN，同教师同时段 → 不冲突（可共槽） */
    @Test
    void checkConflict_oddAndEvenWeekTypeCoexistWithoutTeacherConflict() {
        TeachingTaskVo currentTask = seedActiveResourcesWithWeekType("ODD");
        currentTask.setWeeklyHours(4); // requiredSlots=2，使课时校验通过（已排0+1≤2）
        when(teachingTaskMapper.selectConflictCheckById(1L)).thenReturn(currentTask);
        when(scheduleMapper.selectCount(any())).thenReturn(0L);

        Schedule existingSchedule = new Schedule();
        existingSchedule.setTeachingTaskId(9L);
        existingSchedule.setClassroomId(999L); // 不同教室，避免触发教室冲突
        existingSchedule.setWeekType("EVEN");
        TeachingTask existingTask = new TeachingTask();
        existingTask.setId(9L);
        existingTask.setTeacherId(10L); // 与当前任务同教师
        existingTask.setClassId(888L);  // 不同班级
        existingTask.setWeekType("EVEN");
        when(scheduleMapper.selectList(any())).thenReturn(List.of(existingSchedule));
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of(existingTask));

        String reason = service.checkConflict(1L, 2L, 3L, null);

        assertEquals(null, reason, "单周课与双周课同教师同时段应可共存，不冲突");
    }

    /** V9 单双周：当前 task=ALL，已有 task=ODD，同教师同时段 → 冲突（ALL 与任意冲突） */
    @Test
    void checkConflict_allWeekTypeConflictsWithOddSameTeacher() {
        TeachingTaskVo currentTask = seedActiveResourcesWithWeekType("ALL");
        when(teachingTaskMapper.selectConflictCheckById(1L)).thenReturn(currentTask);

        Schedule existingSchedule = new Schedule();
        existingSchedule.setTeachingTaskId(9L);
        existingSchedule.setClassroomId(999L);
        existingSchedule.setWeekType("ODD");
        TeachingTask existingTask = new TeachingTask();
        existingTask.setId(9L);
        existingTask.setTeacherId(10L); // 同教师
        existingTask.setClassId(888L);
        existingTask.setWeekType("ODD");
        when(scheduleMapper.selectList(any())).thenReturn(List.of(existingSchedule));
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of(existingTask));

        String reason = service.checkConflict(1L, 2L, 3L, null);

        assertTrue(reason != null && reason.startsWith("[TEACHER_CONFLICT]"),
                "全周课与单周课同教师同时段应冲突");
    }

    private void seedActiveResources() {
        seedActiveResourcesWithWeekType(null);
    }

    /** 构造激活的教师/班级/教室/时段 mock，并返回带指定 weekType 的当前任务 VO */
    private TeachingTaskVo seedActiveResourcesWithWeekType(String weekType) {
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
        task.setWeekType(weekType);
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
        return task;
    }
}
