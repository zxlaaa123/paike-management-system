package com.paike.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.V5RepairTaskStatus;
import com.paike.scheduler.entity.ScheduleRepairSuggestion;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.ScheduleRepairSuggestionMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A3：验证 V5RepairSuggestionService 的审计接入（markForSimulation 成功/失败两条路径）。
 * 纯 Mockito，不加载 Spring 上下文。
 */
class V5RepairSuggestionServiceAuditTest {

    @Test
    void markForSimulation_recordsSuccessAuditWhenAccepted() {
        ScheduleRepairTaskMapper repairTaskMapper = mock(ScheduleRepairTaskMapper.class);
        ScheduleRepairSuggestionMapper suggestionMapper = mock(ScheduleRepairSuggestionMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        V5RepairSuggestionService service = newService(repairTaskMapper, suggestionMapper, auditLogService);

        ScheduleRepairTask task = new ScheduleRepairTask();
        task.setId(1L);
        task.setSemesterId(3L);
        task.setPlanId(7L);
        task.setStatus(V5RepairTaskStatus.SUGGESTED.getCode());
        when(repairTaskMapper.selectById(1L)).thenReturn(task);

        ScheduleRepairSuggestion suggestion = new ScheduleRepairSuggestion();
        suggestion.setId(99L);
        suggestion.setRepairTaskId(1L);
        when(suggestionMapper.selectById(99L)).thenReturn(suggestion);

        service.markForSimulation(1L, 99L);

        verify(auditLogService).recordSuccess(
                eq(SystemAuditLogService.ACTION_MARK_REPAIR_SUGGESTION),
                eq(SystemAuditLogService.TARGET_REPAIR_SUGGESTION),
                eq(99L),
                eq(3L),
                eq(7L),
                any());
    }

    @Test
    void markForSimulation_recordsFailureAuditWhenTaskCancelled() {
        ScheduleRepairTaskMapper repairTaskMapper = mock(ScheduleRepairTaskMapper.class);
        ScheduleRepairSuggestionMapper suggestionMapper = mock(ScheduleRepairSuggestionMapper.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        V5RepairSuggestionService service = newService(repairTaskMapper, suggestionMapper, auditLogService);

        ScheduleRepairTask task = new ScheduleRepairTask();
        task.setId(1L);
        task.setSemesterId(3L);
        task.setPlanId(7L);
        task.setStatus(V5RepairTaskStatus.CANCELLED.getCode());
        when(repairTaskMapper.selectById(1L)).thenReturn(task);

        assertThrows(RuntimeException.class, () -> service.markForSimulation(1L, 99L));

        verify(auditLogService).recordFailure(
                eq(SystemAuditLogService.ACTION_MARK_REPAIR_SUGGESTION),
                eq(SystemAuditLogService.TARGET_REPAIR_SUGGESTION),
                eq(99L),
                eq(3L),
                eq(7L),
                eq(SystemAuditLogService.ERROR_BUSINESS),
                any());
    }

    private V5RepairSuggestionService newService(
            ScheduleRepairTaskMapper repairTaskMapper,
            ScheduleRepairSuggestionMapper suggestionMapper,
            SystemAuditLogService auditLogService
    ) {
        return new V5RepairSuggestionService(
                repairTaskMapper,
                suggestionMapper,
                mock(SchedulePlanItemMapper.class),
                mock(ClassroomMapper.class),
                mock(V5CandidatePositionService.class),
                mock(V4ScheduleRiskService.class),
                new ObjectMapper(),
                auditLogService);
    }
}
