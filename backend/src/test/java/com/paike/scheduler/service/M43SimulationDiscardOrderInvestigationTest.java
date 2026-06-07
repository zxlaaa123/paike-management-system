package com.paike.scheduler.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.SchedulePlanStatus;
import com.paike.scheduler.common.enums.V5RepairTaskStatus;
import com.paike.scheduler.service.vo.ScheduleAdjustLogVo;
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
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class M43SimulationDiscardOrderInvestigationTest {

    @Test
    void discardBuildsDetailSnapshotAfterStatusChangeAndBeforeCleanup() {
        Long taskId = 10L;
        Long planId = 20L;
        Long semesterId = 30L;

        ScheduleRepairTaskMapper repairTaskMapper = mock(ScheduleRepairTaskMapper.class);
        ScheduleRepairSuggestionMapper suggestionMapper = mock(ScheduleRepairSuggestionMapper.class);
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        ScheduleScoreDetailMapper scoreDetailMapper = mock(ScheduleScoreDetailMapper.class);
        ScheduleLockedItemMapper lockedItemMapper = mock(ScheduleLockedItemMapper.class);
        SchedulePlanService schedulePlanService = mock(SchedulePlanService.class);
        V4ScheduleRiskService riskService = mock(V4ScheduleRiskService.class);
        SchedulePlanExplainService explainService = mock(SchedulePlanExplainService.class);
        V5ConsistencyCheckService consistencyCheckService = mock(V5ConsistencyCheckService.class);

        V5SimulationService service = new V5SimulationService(
                repairTaskMapper,
                suggestionMapper,
                planMapper,
                planItemMapper,
                mock(ScheduleMapper.class),
                mock(TimeSlotMapper.class),
                mock(ClassroomMapper.class),
                scoreDetailMapper,
                lockedItemMapper,
                mock(ScheduleOptimizationCompareMapper.class),
                schedulePlanService,
                mock(ScheduleScoreService.class),
                riskService,
                mock(V5RuleEvaluationService.class),
                explainService,
                consistencyCheckService,
                new ObjectMapper(),
                mock(PlatformTransactionManager.class),
                mock(SystemAuditLogService.class));

        ScheduleRepairTask task = new ScheduleRepairTask();
        task.setId(taskId);
        task.setStatus(V5RepairTaskStatus.SIMULATED.getCode());
        task.setResultPlanId(planId);

        SchedulePlan plan = new SchedulePlan();
        plan.setId(planId);
        plan.setRepairTaskId(taskId);
        plan.setSemesterId(semesterId);
        plan.setStatus(SchedulePlanStatus.SIMULATION.getCode());
        plan.setName("试算方案");
        plan.setTotalScore(BigDecimal.ZERO);
        plan.setScheduledCount(0);
        plan.setUnscheduledCount(0);
        plan.setConflictCount(0);

        when(repairTaskMapper.selectById(taskId)).thenReturn(task);
        when(planMapper.selectById(planId)).thenReturn(plan);
        when(riskService.getPlanRisks(eq(planId), isNull(), isNull(), isNull())).thenReturn(emptyRisks(planId));
        when(schedulePlanService.getPlanItems(planId)).thenReturn(List.of());
        when(scoreDetailMapper.selectList(any())).thenReturn(List.of());
        Page<ScheduleAdjustLogVo> adjustLogPage = new Page<>();
        adjustLogPage.setRecords(List.of());
        when(explainService.listAdjustLogs(eq(semesterId), eq(planId), isNull(), eq(1), eq(500))).thenReturn(adjustLogPage);
        when(suggestionMapper.selectOne(any())).thenReturn(null);
        when(planItemMapper.delete(any())).thenThrow(new IllegalStateException("cleanup reached after detail"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.discard(taskId, planId));

        assertEquals("cleanup reached after detail", error.getMessage());
        assertEquals(SchedulePlanStatus.DISCARDED.getCode(), plan.getStatus());
        assertEquals(V5RepairTaskStatus.SUGGESTED.getCode(), task.getStatus());
        assertNull(task.getResultPlanId());

        InOrder inOrder = inOrder(planMapper, repairTaskMapper, schedulePlanService, planItemMapper);
        inOrder.verify(planMapper).updateById(plan);
        inOrder.verify(repairTaskMapper).updateById(task);
        inOrder.verify(schedulePlanService, times(2)).getPlanItems(planId);
        inOrder.verify(planItemMapper).delete(any());
    }

    private ScheduleRiskListVo emptyRisks(Long planId) {
        ScheduleRiskListVo vo = new ScheduleRiskListVo();
        vo.setPlanId(planId);
        vo.setRiskCount(0);
        vo.setHighRiskCount(0);
        vo.setMediumRiskCount(0);
        vo.setLowRiskCount(0);
        vo.setUnresolvedCount(0);
        vo.setRisks(List.of());
        return vo;
    }
}
