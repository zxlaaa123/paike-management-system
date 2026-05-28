package com.paike.scheduler.controller;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.common.exception.GlobalExceptionHandler;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.AutoScheduleBatchService;
import com.paike.scheduler.service.AutoScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerNotFoundStatusTest {

    @Test
    void timeSlotMissingThrowsBusinessNotFound() {
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        when(timeSlotMapper.selectById(999L)).thenReturn(null);
        TimeSlotController controller = new TimeSlotController(timeSlotMapper);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.getById(999L));

        assertEquals(404, ex.getCode());
        assertEquals("时间段不存在", ex.getMessage());
    }

    @Test
    void autoScheduleBatchMissingThrowsBusinessNotFound() {
        AutoScheduleBatchService batchService = mock(AutoScheduleBatchService.class);
        when(batchService.getById(999L)).thenReturn(null);
        AutoScheduleBatchController controller = new AutoScheduleBatchController(
                batchService,
                mock(AutoScheduleService.class)
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.getBatchById(999L));

        assertEquals(404, ex.getCode());
        assertEquals("批次不存在", ex.getMessage());
    }

    @Test
    void businessNotFoundUsesHttpNotFoundStatus() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(404, "资源不存在")
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getCode());
        assertEquals("资源不存在", response.getBody().getMessage());
    }
}
