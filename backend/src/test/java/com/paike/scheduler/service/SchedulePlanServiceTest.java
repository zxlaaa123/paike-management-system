package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.dto.SchedulePlanItemAdjustRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SchedulePlanServiceTest {

    @Test
    void adjustPlanItem_rejectsMissingReasonBeforeMutation() {
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        SchedulePlanService service = new SchedulePlanService(
                mock(SchedulePlanMapper.class),
                planItemMapper,
                mock(ScheduleMapper.class),
                mock(ScheduleLockedItemMapper.class),
                mock(ScheduleLockGuardService.class),
                mock(CourseMapper.class),
                mock(TeacherMapper.class),
                mock(ClassInfoMapper.class),
                mock(ClassroomMapper.class),
                mock(TimeSlotMapper.class),
                mock(TeachingTaskMapper.class),
                mock(TeacherUnavailableTimeService.class),
                mock(ScheduleScoreService.class),
                mock(SchedulePlanExplainService.class),
                mock(SystemAuditLogService.class));

        SchedulePlanItemAdjustRequest request = new SchedulePlanItemAdjustRequest();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.adjustPlanItem(1L, request));

        assertEquals("调整原因不能为空", error.getMessage());
        verifyNoInteractions(planItemMapper);
    }

    @Test
    void refreshPlanConflictState_groupsPeerConflictsAndSkipsUnchangedItems() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        TeacherMapper teacherMapper = mock(TeacherMapper.class);
        ClassInfoMapper classInfoMapper = mock(ClassInfoMapper.class);
        ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        SchedulePlanService service = new SchedulePlanService(
                planMapper,
                planItemMapper,
                mock(ScheduleMapper.class),
                mock(ScheduleLockedItemMapper.class),
                mock(ScheduleLockGuardService.class),
                courseMapper,
                teacherMapper,
                classInfoMapper,
                classroomMapper,
                timeSlotMapper,
                teachingTaskMapper,
                mock(TeacherUnavailableTimeService.class),
                mock(ScheduleScoreService.class),
                mock(SchedulePlanExplainService.class),
                mock(SystemAuditLogService.class));

        SchedulePlanItem first = planItem(1L, 101L, 201L, 301L, 401L, 1, 1, 2);
        SchedulePlanItem second = planItem(2L, 102L, 201L, 301L, 401L, 1, 1, 2);
        SchedulePlanItem unchanged = planItem(3L, 103L, 202L, 302L, 402L, 1, 3, 4);
        unchanged.setConflictFlag(0);

        when(planItemMapper.selectList(any())).thenReturn(List.of(first, second, unchanged));
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of(
                teachingTask(101L, 201L, 301L, 501L),
                teachingTask(102L, 201L, 301L, 501L),
                teachingTask(103L, 202L, 302L, 502L)));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(teacher(201L, "张老师"), teacher(202L, "李老师")));
        when(classInfoMapper.selectBatchIds(any())).thenReturn(List.of(classInfo(301L, "一班"), classInfo(302L, "二班")));
        when(classroomMapper.selectBatchIds(any())).thenReturn(List.of(classroom(401L, "A101"), classroom(402L, "B201")));
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(timeSlot(901L, 1, 1), timeSlot(902L, 1, 2)));
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        when(planMapper.selectById(10L)).thenReturn(plan);

        int conflictCount = service.refreshPlanConflictState(10L);

        assertEquals(2, conflictCount);
        verify(planItemMapper, times(2)).updateById(any(SchedulePlanItem.class));
        assertEquals(2, plan.getConflictCount());
        assertEquals("教师时间冲突：张老师；班级时间冲突：一班；教室时间冲突：A101", first.getConflictReason());
        assertEquals("教师时间冲突：张老师；班级时间冲突：一班；教室时间冲突：A101", second.getConflictReason());
    }

    @Test
    void applyPlan_recordsFailureAuditWhenPlanRejected() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        SchedulePlanService service = new SchedulePlanService(
                planMapper,
                mock(SchedulePlanItemMapper.class),
                mock(ScheduleMapper.class),
                mock(ScheduleLockedItemMapper.class),
                mock(ScheduleLockGuardService.class),
                mock(CourseMapper.class),
                mock(TeacherMapper.class),
                mock(ClassInfoMapper.class),
                mock(ClassroomMapper.class),
                mock(TimeSlotMapper.class),
                mock(TeachingTaskMapper.class),
                mock(TeacherUnavailableTimeService.class),
                mock(ScheduleScoreService.class),
                mock(SchedulePlanExplainService.class),
                auditLogService);
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        plan.setSemesterId(3L);
        plan.setStatus("ABANDONED");
        when(planMapper.selectById(10L)).thenReturn(plan);

        BusinessException error = assertThrows(BusinessException.class, () -> service.applyPlan(10L));

        assertEquals("已废弃方案不能应用", error.getMessage());
        verify(auditLogService).recordFailure(
                eq(SystemAuditLogService.ACTION_APPLY_PLAN),
                eq(SystemAuditLogService.TARGET_SCHEDULE_PLAN),
                eq(10L),
                eq(3L),
                eq(10L),
                eq("400"),
                eq("已废弃方案不能应用"));
    }

    private SchedulePlanItem planItem(Long id, Long taskId, Long teacherId, Long classId, Long classroomId,
                                      Integer weekday, Integer startPeriod, Integer endPeriod) {
        SchedulePlanItem item = new SchedulePlanItem();
        item.setId(id);
        item.setPlanId(10L);
        item.setTeachingTaskId(taskId);
        item.setTeacherId(teacherId);
        item.setClassId(classId);
        item.setClassroomId(classroomId);
        item.setWeekday(weekday);
        item.setStartPeriod(startPeriod);
        item.setEndPeriod(endPeriod);
        item.setConflictFlag(0);
        return item;
    }

    private TeachingTask teachingTask(Long id, Long teacherId, Long classId, Long courseId) {
        TeachingTask task = new TeachingTask();
        task.setId(id);
        task.setTeacherId(teacherId);
        task.setClassId(classId);
        task.setCourseId(courseId);
        return task;
    }

    private Teacher teacher(Long id, String name) {
        Teacher teacher = new Teacher();
        teacher.setId(id);
        teacher.setName(name);
        return teacher;
    }

    private ClassInfo classInfo(Long id, String name) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(id);
        classInfo.setClassName(name);
        return classInfo;
    }

    private Classroom classroom(Long id, String name) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setRoomName(name);
        return classroom;
    }

    private TimeSlot timeSlot(Long id, Integer dayOfWeek, Integer periodNo) {
        TimeSlot slot = new TimeSlot();
        slot.setId(id);
        slot.setDayOfWeek(dayOfWeek);
        slot.setPeriodNo(periodNo);
        return slot;
    }
}
