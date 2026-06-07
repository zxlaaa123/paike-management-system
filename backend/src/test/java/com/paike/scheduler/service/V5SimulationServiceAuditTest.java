package com.paike.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.SchedulePlanStatus;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.ScheduleOptimizationCompareMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRepairSuggestionMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.mapper.ScheduleScoreDetailMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.vo.ApplyPlanResultVo;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V5SimulationServiceAuditTest {

    @Test
    void apply_recordsSuccessAuditAfterSimulationApplied() {
        ScheduleRepairTaskMapper repairTaskMapper = mock(ScheduleRepairTaskMapper.class);
        ScheduleRepairSuggestionMapper suggestionMapper = mock(ScheduleRepairSuggestionMapper.class);
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SchedulePlanService schedulePlanService = mock(SchedulePlanService.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        V5SimulationService service = newService(
                repairTaskMapper, suggestionMapper, planMapper, schedulePlanService, auditLogService);
        ScheduleRepairTask task = new ScheduleRepairTask();
        task.setId(10L);
        task.setSemesterId(3L);
        SchedulePlan plan = new SchedulePlan();
        plan.setId(20L);
        plan.setRepairTaskId(10L);
        plan.setSemesterId(3L);
        plan.setStatus(SchedulePlanStatus.SIMULATION.getCode());
        plan.setConflictCount(0);
        plan.setTotalScore(BigDecimal.ZERO);
        plan.setScheduledCount(0);
        plan.setUnscheduledCount(0);
        when(repairTaskMapper.selectById(10L)).thenReturn(task);
        when(planMapper.selectById(20L)).thenReturn(plan);
        when(schedulePlanService.getPlanItems(20L)).thenReturn(List.of());
        when(schedulePlanService.applySimulationPlan(20L)).thenReturn(new ApplyPlanResultVo());

        service.apply(10L, 20L);

        verify(auditLogService).recordSuccess(
                eq(SystemAuditLogService.ACTION_APPLY_SIMULATION_PLAN),
                eq(SystemAuditLogService.TARGET_SCHEDULE_PLAN),
                eq(20L),
                eq(3L),
                eq(20L),
                any());
    }

    @Test
    void apply_recordsFailureAuditWhenTaskMissing() {
        ScheduleRepairTaskMapper repairTaskMapper = mock(ScheduleRepairTaskMapper.class);
        ScheduleRepairSuggestionMapper suggestionMapper = mock(ScheduleRepairSuggestionMapper.class);
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SchedulePlanService schedulePlanService = mock(SchedulePlanService.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        V5SimulationService service = newService(
                repairTaskMapper, suggestionMapper, planMapper, schedulePlanService, auditLogService);

        assertThrows(RuntimeException.class, () -> service.apply(10L, 20L));

        verify(auditLogService).recordFailure(
                eq(SystemAuditLogService.ACTION_APPLY_SIMULATION_PLAN),
                eq(SystemAuditLogService.TARGET_SCHEDULE_PLAN),
                eq(20L),
                eq(null),
                eq(20L),
                eq(SystemAuditLogService.ERROR_BUSINESS),
                any());
    }

    private V5SimulationService newService(
            ScheduleRepairTaskMapper repairTaskMapper,
            ScheduleRepairSuggestionMapper suggestionMapper,
            SchedulePlanMapper planMapper,
            SchedulePlanService schedulePlanService,
            SystemAuditLogService auditLogService
    ) {
        return new V5SimulationService(
                repairTaskMapper,
                suggestionMapper,
                planMapper,
                mock(SchedulePlanItemMapper.class),
                mock(ScheduleMapper.class),
                mock(TimeSlotMapper.class),
                mock(ClassroomMapper.class),
                mock(ScheduleScoreDetailMapper.class),
                mock(ScheduleLockedItemMapper.class),
                mock(ScheduleOptimizationCompareMapper.class),
                schedulePlanService,
                mock(ScheduleScoreService.class),
                mock(V4ScheduleRiskService.class),
                mock(V5RuleEvaluationService.class),
                mock(SchedulePlanExplainService.class),
                mock(V5ConsistencyCheckService.class),
                new ObjectMapper(),
                mock(PlatformTransactionManager.class),
                auditLogService);
    }
}
