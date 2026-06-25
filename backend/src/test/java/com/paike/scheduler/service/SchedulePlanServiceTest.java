package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.enums.SchedulePlanStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.dto.SchedulePlanItemAdjustRequest;
import com.paike.scheduler.service.vo.SchedulePlanVo;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SchedulePlanServiceTest {

    @Test
    void listVo_fillsSemesterNameAndStrategyName() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SemesterMapper semesterMapper = mock(SemesterMapper.class);
        SchedulePlanService service = new SchedulePlanService(
                planMapper,
                semesterMapper,
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
                mock(SystemAuditLogService.class));

        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        plan.setSemesterId(3L);
        plan.setName("测试方案");
        plan.setStrategyType("COMPREHENSIVE");
        Page<SchedulePlan> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(plan));
        when(planMapper.selectPage(any(), any())).thenReturn(page);

        Semester semester = new Semester();
        semester.setId(3L);
        semester.setName("2025-2026 第一学期");
        when(semesterMapper.selectBatchIds(any())).thenReturn(List.of(semester));

        Page<SchedulePlanVo> result = service.listVo(3L, null, null, null, 1, 10);

        assertEquals(1, result.getTotal());
        assertEquals("2025-2026 第一学期", result.getRecords().get(0).getSemesterName());
        assertEquals("综合最优", result.getRecords().get(0).getStrategyName());
    }

    @Test
    void adjustPlanItem_rejectsMissingReasonBeforeMutation() {
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        SchedulePlanService service = new SchedulePlanService(
                mock(SchedulePlanMapper.class),
                mock(SemesterMapper.class),
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
                mock(SemesterMapper.class),
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
    void refreshPlanConflictState_skipsItemUpdatesWhenLargeInputHasNoChanges() {
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
                mock(SemesterMapper.class),
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

        List<SchedulePlanItem> items = new ArrayList<>();
        List<TeachingTask> tasks = new ArrayList<>();
        for (long i = 1; i <= 200; i++) {
            SchedulePlanItem item = planItem(i, 1000L + i, 2000L + i, 3000L + i, 4000L + i, 1, (int) (i * 2 - 1), (int) (i * 2));
            item.setConflictFlag(0);
            item.setConflictReason(null);
            items.add(item);
            tasks.add(teachingTask(1000L + i, 2000L + i, 3000L + i, 5000L + i));
        }

        when(planItemMapper.selectList(any())).thenReturn(items);
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(tasks);
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of());
        when(classInfoMapper.selectBatchIds(any())).thenReturn(List.of());
        when(classroomMapper.selectBatchIds(any())).thenReturn(List.of());
        when(timeSlotMapper.selectList(any())).thenReturn(List.of());
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        when(planMapper.selectById(10L)).thenReturn(plan);

        int conflictCount = service.refreshPlanConflictState(10L);

        assertEquals(0, conflictCount);
        verify(planItemMapper, never()).updateById(any(SchedulePlanItem.class));
        verify(planMapper).updateById(plan);
        assertEquals(0, plan.getConflictCount());
    }

    @Test
    void refreshPlanConflictState_oddEvenSameSlotNoConflict() {
        // V9 单双周：同教师/班级/教室同时段，一条 ODD 一条 EVEN，共享时段合法，应 0 冲突。
        // 改造前（按 weekday+startPeriod 桶聚组 size>1 即冲突）会误报 2 个冲突。
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
                mock(SemesterMapper.class),
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

        // 同教师 201L / 同班级 301L / 同教室 401L / 同时段(周1 第1节)，weekType 互补
        SchedulePlanItem odd = planItem(1L, 101L, 201L, 301L, 401L, 1, 1, 2);
        odd.setWeekType("ODD");
        odd.setCourseId(501L);
        SchedulePlanItem even = planItem(2L, 102L, 201L, 301L, 401L, 1, 1, 2);
        even.setWeekType("EVEN");
        even.setCourseId(502L);

        when(planItemMapper.selectList(any())).thenReturn(List.of(odd, even));
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of(
                teachingTask(101L, 201L, 301L, 501L),
                teachingTask(102L, 201L, 301L, 502L)));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(teacher(201L, "张老师")));
        when(classInfoMapper.selectBatchIds(any())).thenReturn(List.of(classInfo(301L, "一班")));
        when(classroomMapper.selectBatchIds(any())).thenReturn(List.of(classroom(401L, "A101")));
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(timeSlot(901L, 1, 1)));
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        when(planMapper.selectById(10L)).thenReturn(plan);

        int conflictCount = service.refreshPlanConflictState(10L);

        assertEquals(0, conflictCount);
        // 两条 item 都不该被打上冲突标记，因此不该有 updateById
        verify(planItemMapper, never()).updateById(any(SchedulePlanItem.class));
        assertEquals(0, plan.getConflictCount());
        assertEquals(0, odd.getConflictFlag());
        assertEquals(0, even.getConflictFlag());
    }

    @Test
    void refreshPlanConflictState_sameWeekTypeSameSlotConflicts() {
        // 对照组：同教师/班级/教室同时段、同 weekType（都 ODD），应报冲突（2 个 item 都标记）。
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
                mock(SemesterMapper.class),
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

        SchedulePlanItem odd1 = planItem(1L, 101L, 201L, 301L, 401L, 1, 1, 2);
        odd1.setWeekType("ODD");
        SchedulePlanItem odd2 = planItem(2L, 102L, 201L, 301L, 401L, 1, 1, 2);
        odd2.setWeekType("ODD");

        when(planItemMapper.selectList(any())).thenReturn(List.of(odd1, odd2));
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of(
                teachingTask(101L, 201L, 301L, 501L),
                teachingTask(102L, 201L, 301L, 502L)));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(teacher(201L, "张老师")));
        when(classInfoMapper.selectBatchIds(any())).thenReturn(List.of(classInfo(301L, "一班")));
        when(classroomMapper.selectBatchIds(any())).thenReturn(List.of(classroom(401L, "A101")));
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(timeSlot(901L, 1, 1)));
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        when(planMapper.selectById(10L)).thenReturn(plan);

        int conflictCount = service.refreshPlanConflictState(10L);

        assertEquals(2, conflictCount);
        verify(planItemMapper, times(2)).updateById(any(SchedulePlanItem.class));
    }

    /** V10 连续周段：同教师/班级/教室同时段，ALL 1-8 与 ALL 9-16 实际周集合不相交 → 不冲突 */
    @Test
    void refreshPlanConflictState_disjointWeekRangeNoConflict() {
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
                mock(SemesterMapper.class),
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

        SchedulePlanItem a = planItem(1L, 101L, 201L, 301L, 401L, 1, 1, 2);
        a.setWeekType("ALL");
        a.setStartWeek(1);
        a.setEndWeek(8);
        a.setCourseId(501L);
        SchedulePlanItem b = planItem(2L, 102L, 201L, 301L, 401L, 1, 1, 2);
        b.setWeekType("ALL");
        b.setStartWeek(9);
        b.setEndWeek(16);
        b.setCourseId(502L);

        when(planItemMapper.selectList(any())).thenReturn(List.of(a, b));
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of(
                teachingTask(101L, 201L, 301L, 501L),
                teachingTask(102L, 201L, 301L, 502L)));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(teacher(201L, "张老师")));
        when(classInfoMapper.selectBatchIds(any())).thenReturn(List.of(classInfo(301L, "一班")));
        when(classroomMapper.selectBatchIds(any())).thenReturn(List.of(classroom(401L, "A101")));
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(timeSlot(901L, 1, 1)));
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        when(planMapper.selectById(10L)).thenReturn(plan);

        int conflictCount = service.refreshPlanConflictState(10L);

        assertEquals(0, conflictCount, "ALL 1-8 与 ALL 9-16 实际周集合不相交，不应报冲突");
        verify(planItemMapper, never()).updateById(any(SchedulePlanItem.class));
    }

    /** V10：同教师/班级/教室同时段，ALL 1-8 与 ODD 5-12 重叠自然周 5、7 → 冲突 */
    @Test
    void refreshPlanConflictState_overlappingWeekRangeConflicts() {
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
                mock(SemesterMapper.class),
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

        SchedulePlanItem a = planItem(1L, 101L, 201L, 301L, 401L, 1, 1, 2);
        a.setWeekType("ALL");
        a.setStartWeek(1);
        a.setEndWeek(8);
        a.setCourseId(501L);
        SchedulePlanItem b = planItem(2L, 102L, 201L, 301L, 401L, 1, 1, 2);
        b.setWeekType("ODD");
        b.setStartWeek(5);
        b.setEndWeek(12);
        b.setCourseId(502L);

        when(planItemMapper.selectList(any())).thenReturn(List.of(a, b));
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of(
                teachingTask(101L, 201L, 301L, 501L),
                teachingTask(102L, 201L, 301L, 502L)));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(teacher(201L, "张老师")));
        when(classInfoMapper.selectBatchIds(any())).thenReturn(List.of(classInfo(301L, "一班")));
        when(classroomMapper.selectBatchIds(any())).thenReturn(List.of(classroom(401L, "A101")));
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(timeSlot(901L, 1, 1)));
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        when(planMapper.selectById(10L)).thenReturn(plan);

        int conflictCount = service.refreshPlanConflictState(10L);

        assertEquals(2, conflictCount, "ALL 1-8 与 ODD 5-12 重叠自然周 5、7，两条 item 都应标记冲突");
        verify(planItemMapper, times(2)).updateById(any(SchedulePlanItem.class));
    }

    @Test
    void applyPlan_recordsFailureAuditWhenPlanRejected() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        SchedulePlanService service = new SchedulePlanService(
                planMapper,
                mock(SemesterMapper.class),
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
        plan.setStatus(SchedulePlanStatus.ABANDONED.getCode());
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

    @Test
    void applyPlan_clearsTargetSemesterSchedulesBeforeInsertingPlanSchedules() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        ScheduleLockedItemMapper scheduleLockedItemMapper = mock(ScheduleLockedItemMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        TeacherMapper teacherMapper = mock(TeacherMapper.class);
        ClassInfoMapper classInfoMapper = mock(ClassInfoMapper.class);
        ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        TeacherUnavailableTimeService unavailableTimeService = mock(TeacherUnavailableTimeService.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        SemesterMapper semesterMapper = mock(SemesterMapper.class);
        Semester semester = new Semester();
        semester.setId(3L);
        when(semesterMapper.selectByIdForUpdate(3L)).thenReturn(semester);
        SchedulePlanService service = new SchedulePlanService(
                planMapper,
                semesterMapper,
                planItemMapper,
                scheduleMapper,
                scheduleLockedItemMapper,
                mock(ScheduleLockGuardService.class),
                courseMapper,
                teacherMapper,
                classInfoMapper,
                classroomMapper,
                timeSlotMapper,
                teachingTaskMapper,
                unavailableTimeService,
                mock(ScheduleScoreService.class),
                mock(SchedulePlanExplainService.class),
                auditLogService);
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        plan.setSemesterId(3L);
        plan.setStatus(SchedulePlanStatus.DRAFT.getCode());
        plan.setScheduledCount(1);
        when(planMapper.selectById(10L)).thenReturn(plan);
        when(planMapper.selectList(any())).thenReturn(List.of());
        when(planMapper.updateById(any(SchedulePlan.class))).thenReturn(1);

        SchedulePlanItem item = planItem(101L, 201L, 301L, 401L, 501L, 1, 1, 2);
        item.setCourseId(601L);
        // V10 周模式：plan_item 设 ODD + 周段，验证透传到正式 schedule
        item.setWeekType("ODD");
        item.setStartWeek(3);
        item.setEndWeek(15);
        when(planItemMapper.selectList(any())).thenReturn(List.of(item));
        when(planItemMapper.updateById(any(SchedulePlanItem.class))).thenReturn(1);

        TeachingTask task = teachingTask(201L, 301L, 401L, 601L);
        when(teachingTaskMapper.selectBatchIds(any())).thenReturn(List.of(task));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of(new Course()));
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(teacher(301L, "教师A")));
        when(classInfoMapper.selectBatchIds(any())).thenReturn(List.of(classInfo(401L, "班级A")));
        when(classroomMapper.selectBatchIds(any())).thenReturn(List.of(classroom(501L, "教室A")));

        TimeSlot slot = timeSlot(701L, 1, 1);
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(unavailableTimeService.isUnavailable(301L, 701L)).thenReturn(false);

        Schedule existingSchedule = new Schedule();
        existingSchedule.setId(88L);
        existingSchedule.setSemesterId(3L);
        when(scheduleMapper.selectList(any())).thenReturn(List.of(existingSchedule));
        when(scheduleLockedItemMapper.selectCount(any())).thenReturn(0L);
        when(scheduleMapper.delete(any())).thenReturn(1);
        when(scheduleMapper.insert(any(Schedule.class))).thenReturn(1);

        service.applyPlan(10L);

        ArgumentCaptor<LambdaQueryWrapper<Schedule>> deleteCaptor = scheduleWrapperCaptor();
        ArgumentCaptor<Schedule> insertCaptor = ArgumentCaptor.forClass(Schedule.class);
        InOrder order = inOrder(scheduleMapper);
        order.verify(scheduleMapper, times(2)).selectList(any());
        order.verify(scheduleMapper).delete(deleteCaptor.capture());
        order.verify(scheduleMapper).insert(insertCaptor.capture());

        assertWrapperContains(deleteCaptor.getValue(), Schedule.class, "semester_id", 3L);
        assertEquals(3L, insertCaptor.getValue().getSemesterId());
        assertEquals(10L, insertCaptor.getValue().getPlanId());
        // V10 周模式：applyPlan 必须把 plan_item.weekType/startWeek/endWeek 透传到 schedule（之前会丢）
        assertEquals("ODD", insertCaptor.getValue().getWeekType());
        assertEquals(3, insertCaptor.getValue().getStartWeek());
        assertEquals(15, insertCaptor.getValue().getEndWeek());
        verify(auditLogService).recordSuccess(
                eq(SystemAuditLogService.ACTION_APPLY_PLAN),
                eq(SystemAuditLogService.TARGET_SCHEDULE_PLAN),
                eq(10L),
                eq(3L),
                eq(10L),
                eq("正式课表已应用，排课数=1"));
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

    @Test
    void applyPlan_acquiresSemesterRowLockBeforeMutation() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SemesterMapper semesterMapper = mock(SemesterMapper.class);
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);

        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        plan.setSemesterId(3L);
        plan.setStatus(SchedulePlanStatus.DRAFT.getCode());
        plan.setScheduledCount(1);
        when(planMapper.selectById(10L)).thenReturn(plan);
        when(planMapper.selectList(any())).thenReturn(List.of());
        when(planMapper.updateById(any(SchedulePlan.class))).thenReturn(1);

        Semester semester = new Semester();
        semester.setId(3L);
        when(semesterMapper.selectByIdForUpdate(3L)).thenReturn(semester);

        SchedulePlanItem item = planItem(101L, 201L, 301L, 401L, 501L, 1, 1, 2);
        item.setCourseId(601L);
        when(planItemMapper.selectList(any())).thenReturn(List.of(item));
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(timeSlot(701L, 1, 1)));
        ScheduleLockedItemMapper lockedItemMapper = mock(ScheduleLockedItemMapper.class);
        when(lockedItemMapper.selectCount(any())).thenReturn(0L);
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(scheduleMapper.delete(any())).thenReturn(0);
        when(scheduleMapper.insert(any(Schedule.class))).thenReturn(1);

        SchedulePlanService service = new SchedulePlanService(
                planMapper, semesterMapper, planItemMapper, scheduleMapper,
                lockedItemMapper, mock(ScheduleLockGuardService.class),
                mock(CourseMapper.class), mock(TeacherMapper.class), mock(ClassInfoMapper.class),
                mock(ClassroomMapper.class), timeSlotMapper, mock(TeachingTaskMapper.class),
                mock(TeacherUnavailableTimeService.class), mock(ScheduleScoreService.class),
                mock(SchedulePlanExplainService.class), auditLogService);

        service.applyPlan(10L);

        // 验证：学期行锁在 planMapper.selectList 之前获取
        InOrder order = inOrder(semesterMapper, planMapper, scheduleMapper);
        order.verify(semesterMapper).selectByIdForUpdate(3L);
        order.verify(planMapper).selectById(10L);
    }

    @Test
    void applyPlan_rejectsWhenSemesterNotFoundAfterLock() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SemesterMapper semesterMapper = mock(SemesterMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);

        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        plan.setSemesterId(999L);
        plan.setStatus(SchedulePlanStatus.DRAFT.getCode());
        plan.setScheduledCount(1);
        when(planMapper.selectById(10L)).thenReturn(plan);
        when(semesterMapper.selectByIdForUpdate(999L)).thenReturn(null);

        SchedulePlanService service = new SchedulePlanService(
                planMapper, semesterMapper, mock(SchedulePlanItemMapper.class),
                mock(ScheduleMapper.class), mock(ScheduleLockedItemMapper.class),
                mock(ScheduleLockGuardService.class), mock(CourseMapper.class),
                mock(TeacherMapper.class), mock(ClassInfoMapper.class), mock(ClassroomMapper.class),
                mock(TimeSlotMapper.class), mock(TeachingTaskMapper.class),
                mock(TeacherUnavailableTimeService.class), mock(ScheduleScoreService.class),
                mock(SchedulePlanExplainService.class), auditLogService);

        BusinessException error = assertThrows(BusinessException.class, () -> service.applyPlan(10L));
        assertEquals("学期不存在，无法应用方案", error.getMessage());
    }

    @Test
    void applyPlan_rejectsDuplicateApplyAfterLock() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SemesterMapper semesterMapper = mock(SemesterMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);

        // 第一次 selectById 返回 DRAFT（通过预检查），获锁后第二次返回 APPLIED（被并发应用了）
        SchedulePlan draftPlan = new SchedulePlan();
        draftPlan.setId(10L);
        draftPlan.setSemesterId(3L);
        draftPlan.setStatus(SchedulePlanStatus.DRAFT.getCode());
        draftPlan.setScheduledCount(1);

        SchedulePlan appliedPlan = new SchedulePlan();
        appliedPlan.setId(10L);
        appliedPlan.setSemesterId(3L);
        appliedPlan.setStatus(SchedulePlanStatus.APPLIED.getCode());
        appliedPlan.setScheduledCount(1);

        when(planMapper.selectById(10L)).thenReturn(draftPlan, appliedPlan);
        Semester semester = new Semester();
        semester.setId(3L);
        when(semesterMapper.selectByIdForUpdate(3L)).thenReturn(semester);

        SchedulePlanService service = new SchedulePlanService(
                planMapper, semesterMapper, mock(SchedulePlanItemMapper.class),
                mock(ScheduleMapper.class), mock(ScheduleLockedItemMapper.class),
                mock(ScheduleLockGuardService.class), mock(CourseMapper.class),
                mock(TeacherMapper.class), mock(ClassInfoMapper.class), mock(ClassroomMapper.class),
                mock(TimeSlotMapper.class), mock(TeachingTaskMapper.class),
                mock(TeacherUnavailableTimeService.class), mock(ScheduleScoreService.class),
                mock(SchedulePlanExplainService.class), auditLogService);

        BusinessException error = assertThrows(BusinessException.class, () -> service.applyPlan(10L));
        assertEquals("该方案已应用，无需重复应用", error.getMessage());
    }

    @Test
    void applyPlan_auditsOldScheduleClearanceAndOldPlanRevert() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SemesterMapper semesterMapper = mock(SemesterMapper.class);
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        ScheduleLockedItemMapper scheduleLockedItemMapper = mock(ScheduleLockedItemMapper.class);
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);

        SchedulePlan newPlan = new SchedulePlan();
        newPlan.setId(20L);
        newPlan.setSemesterId(3L);
        newPlan.setStatus(SchedulePlanStatus.DRAFT.getCode());
        newPlan.setScheduledCount(1);
        when(planMapper.selectById(20L)).thenReturn(newPlan);
        when(planMapper.updateById(any(SchedulePlan.class))).thenReturn(1);

        // 旧已应用方案
        SchedulePlan oldPlan = new SchedulePlan();
        oldPlan.setId(10L);
        oldPlan.setSemesterId(3L);
        oldPlan.setStatus(SchedulePlanStatus.APPLIED.getCode());
        when(planMapper.selectList(any())).thenReturn(List.of(oldPlan));

        Semester semester = new Semester();
        semester.setId(3L);
        when(semesterMapper.selectByIdForUpdate(3L)).thenReturn(semester);

        // 旧课表记录
        Schedule oldSchedule = new Schedule();
        oldSchedule.setId(88L);
        oldSchedule.setSemesterId(3L);
        when(scheduleMapper.selectList(any())).thenReturn(List.of(oldSchedule));
        when(scheduleLockedItemMapper.selectCount(any())).thenReturn(0L);
        when(scheduleMapper.delete(any())).thenReturn(1);
        when(scheduleMapper.insert(any(Schedule.class))).thenReturn(1);

        SchedulePlanItem item = planItem(101L, 201L, 301L, 401L, 501L, 1, 1, 2);
        item.setCourseId(601L);
        when(planItemMapper.selectList(any())).thenReturn(List.of(item));
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(timeSlot(701L, 1, 1)));

        SchedulePlanService service = new SchedulePlanService(
                planMapper, semesterMapper, planItemMapper, scheduleMapper,
                scheduleLockedItemMapper, mock(ScheduleLockGuardService.class),
                mock(CourseMapper.class), mock(TeacherMapper.class), mock(ClassInfoMapper.class),
                mock(ClassroomMapper.class), timeSlotMapper, mock(TeachingTaskMapper.class),
                mock(TeacherUnavailableTimeService.class), mock(ScheduleScoreService.class),
                mock(SchedulePlanExplainService.class), auditLogService);

        service.applyPlan(20L);

        // 验证审计：清理旧课表
        verify(auditLogService).recordSuccess(
                eq(SystemAuditLogService.ACTION_CLEAR_SEMESTER_SCHEDULES),
                eq(SystemAuditLogService.TARGET_SCHEDULE),
                eq(3L),
                eq(3L),
                eq(20L),
                eq("应用方案前清理旧课表，删除 1 条记录"));
        // 验证审计：旧方案回退
        verify(auditLogService).recordSuccess(
                eq(SystemAuditLogService.ACTION_REVERT_APPLIED_PLAN),
                eq(SystemAuditLogService.TARGET_SCHEDULE_PLAN),
                eq(10L),
                eq(3L),
                eq(10L),
                eq("方案被新方案(id=20)覆盖，回退为 DRAFT"));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<Schedule>> scheduleWrapperCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }
}
