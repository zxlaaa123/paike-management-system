package com.paike.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.service.dto.V5RepairTaskFlowCreateRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
}
