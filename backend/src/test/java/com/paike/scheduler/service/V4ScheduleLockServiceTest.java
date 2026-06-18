package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleLockedItem;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.dto.ScheduleLockRequest;
import com.paike.scheduler.service.vo.ScheduleLockActionVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V4ScheduleLockServiceTest {

    private ScheduleLockedItemMapper scheduleLockedItemMapper;
    private SchedulePlanMapper schedulePlanMapper;
    private SchedulePlanItemMapper schedulePlanItemMapper;
    private ScheduleMapper scheduleMapper;
    private SchedulePlanService schedulePlanService;
    private TimeSlotMapper timeSlotMapper;
    private SystemAuditLogService auditLogService;
    private V4ScheduleLockService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ScheduleLockedItem.class);
        scheduleLockedItemMapper = mock(ScheduleLockedItemMapper.class);
        schedulePlanMapper = mock(SchedulePlanMapper.class);
        schedulePlanItemMapper = mock(SchedulePlanItemMapper.class);
        scheduleMapper = mock(ScheduleMapper.class);
        schedulePlanService = mock(SchedulePlanService.class);
        timeSlotMapper = mock(TimeSlotMapper.class);
        auditLogService = mock(SystemAuditLogService.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(transactionCallback())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        service = new V4ScheduleLockService(
                scheduleLockedItemMapper,
                schedulePlanMapper,
                schedulePlanItemMapper,
                scheduleMapper,
                schedulePlanService,
                mock(CourseMapper.class),
                mock(TeacherMapper.class),
                mock(ClassInfoMapper.class),
                mock(ClassroomMapper.class),
                timeSlotMapper,
                transactionTemplate,
                auditLogService);
    }

    @Test
    void lock_recordsPlanItemAuditAfterSuccess() {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(11L);
        plan.setSemesterId(3L);
        SchedulePlanItem item = new SchedulePlanItem();
        item.setId(22L);
        item.setPlanId(11L);
        when(schedulePlanMapper.selectById(11L)).thenReturn(plan);
        when(schedulePlanItemMapper.selectById(22L)).thenReturn(item);
        when(scheduleLockedItemMapper.selectOne(any())).thenReturn(null);
        when(scheduleLockedItemMapper.insert(any(ScheduleLockedItem.class))).thenAnswer(invocation -> {
            ScheduleLockedItem record = invocation.getArgument(0);
            record.setId(900L);
            return 1;
        });

        ScheduleLockRequest request = new ScheduleLockRequest();
        request.setTargetType("PLAN");
        request.setPlanId(11L);
        request.setPlanItemId(22L);
        request.setLockReason("固定示范课");

        ScheduleLockActionVo result = service.lock(request);

        assertTrue(result.getLocked());
        assertEquals(900L, result.getLockId());
        verify(auditLogService).recordSuccess(
                eq(SystemAuditLogService.ACTION_LOCK_PLAN_ITEM),
                eq(SystemAuditLogService.TARGET_SCHEDULE_PLAN_ITEM),
                eq(22L),
                eq(3L),
                eq(11L),
                contains("锁定记录=900"));
    }

    @Test
    void unlock_recordsScheduleAuditAfterSuccess() {
        Schedule schedule = new Schedule();
        schedule.setId(44L);
        schedule.setSemesterId(5L);
        schedule.setPlanId(12L);
        schedule.setDeleted(0);
        ScheduleLockedItem existing = new ScheduleLockedItem();
        existing.setId(901L);
        existing.setTargetType("SCHEDULE");
        existing.setPlanId(12L);
        existing.setScheduleId(44L);
        when(scheduleMapper.selectById(44L)).thenReturn(schedule);
        when(scheduleLockedItemMapper.selectOne(any())).thenReturn(existing);
        when(scheduleLockedItemMapper.update(any(), any())).thenReturn(1);

        ScheduleLockRequest request = new ScheduleLockRequest();
        request.setTargetType("SCHEDULE");
        request.setScheduleId(44L);

        ScheduleLockActionVo result = service.unlock(request);

        assertTrue(result.getUnlocked());
        assertEquals(901L, result.getLockId());
        verify(auditLogService).recordSuccess(
                eq(SystemAuditLogService.ACTION_UNLOCK_SCHEDULE),
                eq(SystemAuditLogService.TARGET_SCHEDULE),
                eq(44L),
                eq(5L),
                eq(12L),
                contains("锁定记录=901"));
    }

    @Test
    void listPlanLocks_expandsScheduleTimeSlotPeriod() {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(11L);
        plan.setName("当前方案");
        ScheduleLockedItem lock = new ScheduleLockedItem();
        lock.setId(902L);
        lock.setTargetType("SCHEDULE");
        lock.setPlanId(11L);
        lock.setScheduleId(44L);
        Schedule schedule = new Schedule();
        schedule.setId(44L);
        schedule.setTimeSlotId(2L);
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setId(2L);
        timeSlot.setDayOfWeek(1);
        timeSlot.setPeriodNo(2);
        when(schedulePlanMapper.selectById(11L)).thenReturn(plan);
        when(scheduleLockedItemMapper.selectList(any())).thenReturn(List.of(lock));
        when(schedulePlanService.getPlanItems(11L)).thenReturn(List.of());
        when(scheduleMapper.selectBatchIds(List.of(44L))).thenReturn(List.of(schedule));
        when(timeSlotMapper.selectBatchIds(List.of(2L))).thenReturn(List.of(timeSlot));

        String period = service.listPlanLocks(11L).getItems().get(0).getPeriod();

        assertEquals("3-4", period);
    }

    @SuppressWarnings("unchecked")
    private static TransactionCallback<Object> transactionCallback() {
        return any(TransactionCallback.class);
    }
}
