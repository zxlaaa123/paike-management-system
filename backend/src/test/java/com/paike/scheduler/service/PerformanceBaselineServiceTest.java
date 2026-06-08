package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.PerformanceBaselineRecord;
import com.paike.scheduler.mapper.PerformanceBaselineRecordMapper;
import com.paike.scheduler.service.vo.PerformanceSummaryVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerformanceBaselineServiceTest {

    private PerformanceBaselineRecordMapper performanceMapper;
    private PerformanceBaselineService service;

    @BeforeEach
    void setUp() {
        performanceMapper = mock(PerformanceBaselineRecordMapper.class);
        service = new PerformanceBaselineService(performanceMapper);
    }

    @Test
    void list_appliesPaginationAndReturnsMapperResult() {
        Page<PerformanceBaselineRecord> expected = new Page<>(2, 20);
        when(performanceMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(expected);

        Page<PerformanceBaselineRecord> result = service.list("AUTO_SCHEDULE", 1L, 2L, true, 2, 20);

        assertEquals(expected, result);
        ArgumentCaptor<Page<PerformanceBaselineRecord>> captor = ArgumentCaptor.forClass(Page.class);
        verify(performanceMapper).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
        assertEquals(2, captor.getValue().getCurrent());
        assertEquals(20, captor.getValue().getSize());
    }

    @Test
    void recordSafely_insertsPerformanceRecord() {
        service.recordSafely(
                PerformanceBaselineService.OP_AUTO_SCHEDULE,
                1L,
                2L,
                3L,
                4,
                5,
                123L,
                true,
                null,
                null,
                null);

        ArgumentCaptor<PerformanceBaselineRecord> captor = ArgumentCaptor.forClass(PerformanceBaselineRecord.class);
        verify(performanceMapper).insert(captor.capture());
        PerformanceBaselineRecord record = captor.getValue();
        assertEquals("AUTO_SCHEDULE", record.getOperationType());
        assertEquals(123L, record.getDurationMs());
        assertEquals(1, record.getSuccess());
    }

    @Test
    void summary_groupsByOperationType() {
        PerformanceBaselineRecord first = new PerformanceBaselineRecord();
        first.setOperationType("AUTO_SCHEDULE");
        first.setDurationMs(100L);
        first.setSuccess(1);
        PerformanceBaselineRecord second = new PerformanceBaselineRecord();
        second.setOperationType("AUTO_SCHEDULE");
        second.setDurationMs(300L);
        second.setSuccess(0);
        when(performanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));

        List<PerformanceSummaryVo> result = service.summary();

        assertEquals(1, result.size());
        assertEquals("AUTO_SCHEDULE", result.get(0).getOperationType());
        assertEquals(2L, result.get(0).getTotalCount());
        assertEquals(1L, result.get(0).getSuccessCount());
        assertEquals(1L, result.get(0).getFailureCount());
        assertEquals(200L, result.get(0).getAvgDurationMs());
        assertEquals(300L, result.get(0).getMaxDurationMs());
    }
}

