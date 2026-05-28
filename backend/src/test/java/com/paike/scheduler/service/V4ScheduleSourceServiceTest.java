package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.mapper.ScheduleAdjustLogMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V4ScheduleSourceServiceTest {

    @Test
    void getCurrentSourceRejectsUnknownTermId() {
        SemesterService semesterService = mock(SemesterService.class);
        when(semesterService.getById(99L)).thenReturn(null);
        V4ScheduleSourceService service = new V4ScheduleSourceService(
                mock(ScheduleMapper.class),
                mock(SchedulePlanMapper.class),
                mock(ScheduleAdjustLogMapper.class),
                semesterService,
                mock(SchedulePlanExplainService.class)
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getCurrentSource(99L));

        assertEquals("学期不存在", ex.getMessage());
    }
}
