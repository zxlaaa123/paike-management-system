package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.mapper.ScheduleConsistencyCheckMapper;
import com.paike.scheduler.mapper.ScheduleRegressionTestMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.service.dto.V5RepairTaskCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class V5Stage1DataServiceTest {

    private ScheduleRepairTaskMapper repairTaskMapper;
    private V5Stage1DataService service;

    @BeforeEach
    void setUp() {
        repairTaskMapper = mock(ScheduleRepairTaskMapper.class);
        service = new V5Stage1DataService(
                repairTaskMapper,
                mock(ScheduleConsistencyCheckMapper.class),
                mock(ScheduleRegressionTestMapper.class));
    }

    @Test
    void createRepairTask_rejectsMissingTaskCode() {
        V5RepairTaskCreateRequest request = new V5RepairTaskCreateRequest();
        request.setTaskType("REPAIR");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createRepairTask(request));

        assertEquals("任务编号不能为空", error.getMessage());
        verifyNoInteractions(repairTaskMapper);
    }

    @Test
    void createRepairTask_rejectsBlankTaskType() {
        V5RepairTaskCreateRequest request = new V5RepairTaskCreateRequest();
        request.setTaskCode("TASK-1");
        request.setTaskType("  ");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createRepairTask(request));

        assertEquals("任务类型不能为空", error.getMessage());
        verifyNoInteractions(repairTaskMapper);
    }
}
