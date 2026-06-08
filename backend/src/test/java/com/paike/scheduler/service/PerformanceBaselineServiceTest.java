package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.PerformanceBaselineRecord;
import com.paike.scheduler.mapper.PerformanceBaselineRecordMapper;
import com.paike.scheduler.service.vo.PerformanceSummaryVo;
import com.paike.scheduler.service.vo.PerformanceTrendVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
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

    @Test
    void trends_comparesWithPreviousRecordAndMarksSlowOperation() {
        PerformanceBaselineRecord latest = record(3L, "AUTO_SCHEDULE", 6000L, 1, LocalDateTime.parse("2026-06-08T10:10:00"));
        PerformanceBaselineRecord previous = record(2L, "AUTO_SCHEDULE", 3000L, 1, LocalDateTime.parse("2026-06-08T10:00:00"));
        PerformanceBaselineRecord other = record(1L, "V5_LOCAL_REPLAN", 1000L, 1, LocalDateTime.parse("2026-06-08T09:00:00"));
        when(performanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(latest, previous, other));

        List<PerformanceTrendVo> result = service.trends(null, 20);

        assertEquals(3, result.size());
        PerformanceTrendVo trend = result.get(0);
        assertEquals(3L, trend.getId());
        assertEquals("AUTO_SCHEDULE", trend.getOperationType());
        assertEquals(6000L, trend.getDurationMs());
        assertEquals(3000L, trend.getPreviousDurationMs());
        assertEquals(3000L, trend.getDurationDeltaMs());
        assertEquals(100, trend.getDurationChangePercent());
        assertEquals(true, trend.getSlowOperation());
        assertEquals(PerformanceBaselineService.SLOW_OPERATION_THRESHOLD_MS, trend.getSlowThresholdMs());
    }

    private PerformanceBaselineRecord record(Long id, String operationType, Long durationMs, Integer success, LocalDateTime createdAt) {
        PerformanceBaselineRecord record = new PerformanceBaselineRecord();
        record.setId(id);
        record.setOperationType(operationType);
        record.setDurationMs(durationMs);
        record.setSuccess(success);
        record.setCreatedAt(createdAt);
        return record;
    }
}
