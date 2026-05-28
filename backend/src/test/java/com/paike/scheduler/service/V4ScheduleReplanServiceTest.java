package com.paike.scheduler.service;

import com.paike.scheduler.common.enums.SchedulePlanStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ScheduleLockedItem;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleUnassignedTaskMapper;
import com.paike.scheduler.service.dto.V4ScheduleReplanRequest;
import com.paike.scheduler.service.vo.ScheduleReplanResultVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V4ScheduleReplanServiceTest {

    private SchedulePlanMapper schedulePlanMapper;
    private SchedulePlanItemMapper schedulePlanItemMapper;
    private ScheduleLockedItemMapper scheduleLockedItemMapper;
    private ScheduleUnassignedTaskMapper scheduleUnassignedTaskMapper;
    private SchedulePlanService schedulePlanService;
    private ScheduleScoreService scheduleScoreService;
    private SchedulePlanExplainService schedulePlanExplainService;
    private V4ScheduleReplanService service;

    @BeforeEach
    void setUp() {
        schedulePlanMapper = mock(SchedulePlanMapper.class);
        schedulePlanItemMapper = mock(SchedulePlanItemMapper.class);
        scheduleLockedItemMapper = mock(ScheduleLockedItemMapper.class);
        scheduleUnassignedTaskMapper = mock(ScheduleUnassignedTaskMapper.class);
        schedulePlanService = mock(SchedulePlanService.class);
        scheduleScoreService = mock(ScheduleScoreService.class);
        schedulePlanExplainService = mock(SchedulePlanExplainService.class);
        service = new V4ScheduleReplanService(
                schedulePlanMapper,
                schedulePlanItemMapper,
                scheduleLockedItemMapper,
                scheduleUnassignedTaskMapper,
                schedulePlanService,
                scheduleScoreService,
                schedulePlanExplainService);
    }

    @Test
    void createLocalReplanPlan_rejectsKeepLockedFalseBeforeCreatingPlan() {
        when(schedulePlanMapper.selectById(10L)).thenReturn(sourcePlan());
        when(schedulePlanItemMapper.selectList(any())).thenReturn(List.of(sourceItem()));
        V4ScheduleReplanRequest request = new V4ScheduleReplanRequest();
        request.setKeepLocked(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createLocalReplanPlan(10L, request));

        assertEquals("V5 修复约束：锁定课程不可移动，局部重排必须保留锁定项", error.getMessage());
        verify(schedulePlanMapper, never()).insert(any(SchedulePlan.class));
        verifyNoInteractions(scheduleLockedItemMapper, scheduleUnassignedTaskMapper, schedulePlanService,
                scheduleScoreService, schedulePlanExplainService);
    }

    @Test
    void createLocalReplanPlan_copiesLocksWhenKeepLockedDefaultsToTrue() {
        AtomicReference<SchedulePlan> insertedPlan = new AtomicReference<>();
        when(schedulePlanMapper.selectById(10L)).thenReturn(sourcePlan());
        when(schedulePlanMapper.selectById(20L)).thenAnswer(invocation -> insertedPlan.get());
        when(schedulePlanItemMapper.selectList(any())).thenReturn(List.of(sourceItem()));
        when(scheduleLockedItemMapper.selectList(any())).thenReturn(List.of(sourceLock()));
        when(scheduleUnassignedTaskMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            SchedulePlan plan = invocation.getArgument(0);
            plan.setId(20L);
            insertedPlan.set(plan);
            return 1;
        }).when(schedulePlanMapper).insert(any(SchedulePlan.class));
        doAnswer(invocation -> {
            SchedulePlanItem item = invocation.getArgument(0);
            item.setId(201L);
            return 1;
        }).when(schedulePlanItemMapper).insert(any(SchedulePlanItem.class));

        ScheduleReplanResultVo result = service.createLocalReplanPlan(10L, null);

        assertEquals(20L, result.getNewPlanId());
        assertEquals(true, result.getKeepLocked());
        assertEquals(1, result.getLockedCount());
        assertTrue(insertedPlan.get().getDescription().contains("保留锁定课程=是"));
        ArgumentCaptor<ScheduleLockedItem> lockCaptor = ArgumentCaptor.forClass(ScheduleLockedItem.class);
        verify(scheduleLockedItemMapper).insert(lockCaptor.capture());
        assertEquals(20L, lockCaptor.getValue().getPlanId());
        assertEquals(201L, lockCaptor.getValue().getPlanItemId());
        assertEquals(1, lockCaptor.getValue().getActiveFlag());
    }

    private SchedulePlan sourcePlan() {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(10L);
        plan.setSemesterId(1L);
        plan.setName("源方案");
        plan.setStrategyType("COMPREHENSIVE");
        plan.setStatus(SchedulePlanStatus.DRAFT.getCode());
        plan.setScheduledCount(1);
        plan.setUnscheduledCount(0);
        plan.setConflictCount(0);
        plan.setTotalScore(new BigDecimal("90"));
        return plan;
    }

    private SchedulePlanItem sourceItem() {
        SchedulePlanItem item = new SchedulePlanItem();
        item.setId(101L);
        item.setPlanId(10L);
        item.setSemesterId(1L);
        item.setTeachingTaskId(301L);
        item.setTeacherId(401L);
        item.setClassId(501L);
        item.setCourseId(601L);
        item.setClassroomId(701L);
        item.setWeekday(1);
        item.setStartPeriod(1);
        item.setEndPeriod(2);
        item.setWeekType("ALL");
        item.setScore(new BigDecimal("90"));
        item.setConflictFlag(0);
        item.setSourceType("AUTO");
        return item;
    }

    private ScheduleLockedItem sourceLock() {
        ScheduleLockedItem lock = new ScheduleLockedItem();
        lock.setId(1001L);
        lock.setTargetType("PLAN");
        lock.setPlanId(10L);
        lock.setPlanItemId(101L);
        lock.setLockReason("重要课程");
        lock.setActiveFlag(1);
        return lock;
    }
}
