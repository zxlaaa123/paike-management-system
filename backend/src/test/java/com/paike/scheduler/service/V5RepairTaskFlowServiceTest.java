package com.paike.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.V5RepairTaskStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.service.dto.V5RepairTaskFlowCreateRequest;
import com.paike.scheduler.service.dto.V5RepairTaskStatusUpdateRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V5RepairTaskFlowServiceTest {

    @Test
    void createTask_rejectsMissingTaskTypeBeforeLookingUpBindings() {
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        V5RepairTaskFlowService service = new V5RepairTaskFlowService(
                mock(ScheduleRepairTaskMapper.class),
                planMapper,
                scheduleMapper,
                mock(V4ScheduleRiskService.class),
                mock(ObjectMapper.class));
        V5RepairTaskFlowCreateRequest request = new V5RepairTaskFlowCreateRequest();
        request.setPlanId(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createTask(request));

        assertEquals("任务类型不能为空", error.getMessage());
        verifyNoInteractions(planMapper, scheduleMapper);
    }

    @Test
    void updateStatus_normalizesRequestStatusThroughEnumCode() {
        ScheduleRepairTaskMapper repairTaskMapper = mock(ScheduleRepairTaskMapper.class);
        V5RepairTaskFlowService service = newService(repairTaskMapper);
        ScheduleRepairTask task = new ScheduleRepairTask();
        task.setId(10L);
        task.setStatus(V5RepairTaskStatus.SUGGESTED.getCode());
        when(repairTaskMapper.selectById(10L)).thenReturn(task);
        V5RepairTaskStatusUpdateRequest request = new V5RepairTaskStatusUpdateRequest();
        request.setStatus("applied");

        service.updateStatus(10L, request);

        assertEquals(V5RepairTaskStatus.APPLIED.getCode(), task.getStatus());
        verify(repairTaskMapper).updateById(task);
    }

    @Test
    void updateStatus_rejectsUnsupportedStatusBeforeMutation() {
        ScheduleRepairTaskMapper repairTaskMapper = mock(ScheduleRepairTaskMapper.class);
        V5RepairTaskFlowService service = newService(repairTaskMapper);
        ScheduleRepairTask task = new ScheduleRepairTask();
        task.setId(10L);
        task.setStatus(V5RepairTaskStatus.SUGGESTED.getCode());
        when(repairTaskMapper.selectById(10L)).thenReturn(task);
        V5RepairTaskStatusUpdateRequest request = new V5RepairTaskStatusUpdateRequest();
        request.setStatus("unknown");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatus(10L, request));

        assertEquals("不支持的修复任务状态：UNKNOWN", error.getMessage());
        assertEquals(V5RepairTaskStatus.SUGGESTED.getCode(), task.getStatus());
        verify(repairTaskMapper, never()).updateById(task);
    }

    private V5RepairTaskFlowService newService(ScheduleRepairTaskMapper repairTaskMapper) {
        return new V5RepairTaskFlowService(
                repairTaskMapper,
                mock(SchedulePlanMapper.class),
                mock(ScheduleMapper.class),
                mock(V4ScheduleRiskService.class),
                mock(ObjectMapper.class));
    }
}
