package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.AutoScheduleBatch;
import com.paike.scheduler.mapper.AutoScheduleBatchMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoScheduleBatchServiceTest {

    @Test
    void createBatch_retriesWhenBatchNoCollides() {
        AutoScheduleBatchMapper batchMapper = mock(AutoScheduleBatchMapper.class);
        AutoScheduleBatchService service = new AutoScheduleBatchService(batchMapper, mock(ScheduleMapper.class));
        when(batchMapper.insert(any(AutoScheduleBatch.class)))
                .thenThrow(new DuplicateKeyException("duplicate batch_no"))
                .thenReturn(1);

        AutoScheduleBatch batch = service.createBatch(2L, 3, true);

        assertEquals(2L, batch.getSemesterId());
        assertNotNull(batch.getBatchNo());
        assertEquals(3, batch.getTotalTaskCount());
        assertEquals(1, batch.getClearOldSchedule());
        assertEquals("RUNNING", batch.getStatus());
        verify(batchMapper, times(2)).insert(any(AutoScheduleBatch.class));
    }

    @Test
    void createBatch_failsAfterRetryExhausted() {
        AutoScheduleBatchMapper batchMapper = mock(AutoScheduleBatchMapper.class);
        AutoScheduleBatchService service = new AutoScheduleBatchService(batchMapper, mock(ScheduleMapper.class));
        when(batchMapper.insert(any(AutoScheduleBatch.class)))
                .thenThrow(new DuplicateKeyException("duplicate batch_no"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createBatch(2L, 3, false));

        assertEquals("自动排课批次号生成失败，请重试", ex.getMessage());
        verify(batchMapper, times(5)).insert(any(AutoScheduleBatch.class));
    }
}
