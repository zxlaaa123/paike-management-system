package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.PerformanceBaselineRecord;
import com.paike.scheduler.mapper.PerformanceBaselineRecordMapper;
import com.paike.scheduler.service.vo.PerformanceSummaryVo;
import com.paike.scheduler.service.vo.PerformanceTrendVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PerformanceBaselineService {

    public static final String OP_AUTO_SCHEDULE = "AUTO_SCHEDULE";
    public static final String OP_V4_LOCAL_REPLAN = "V4_LOCAL_REPLAN";
    public static final String OP_V5_GENERATE_SIMULATION = "V5_GENERATE_SIMULATION";
    public static final String OP_V5_LOCAL_REPLAN = "V5_LOCAL_REPLAN";
    public static final String OP_V5_APPLY_SIMULATION = "V5_APPLY_SIMULATION";
    public static final String OP_V8_SOLVER_GENERATE = "V8_SOLVER_GENERATE";
    public static final long SLOW_OPERATION_THRESHOLD_MS = 5000L;

    private final PerformanceBaselineRecordMapper performanceMapper;

    public Page<PerformanceBaselineRecord> list(String operationType, Long semesterId, Long planId, Boolean success,
                                                int page, int size) {
        LambdaQueryWrapper<PerformanceBaselineRecord> wrapper = new LambdaQueryWrapper<>();
        if (hasText(operationType)) {
            wrapper.eq(PerformanceBaselineRecord::getOperationType, operationType.trim());
        }
        if (semesterId != null) {
            wrapper.eq(PerformanceBaselineRecord::getSemesterId, semesterId);
        }
        if (planId != null) {
            wrapper.eq(PerformanceBaselineRecord::getPlanId, planId);
        }
        if (success != null) {
            wrapper.eq(PerformanceBaselineRecord::getSuccess, success ? 1 : 0);
        }
        wrapper.orderByDesc(PerformanceBaselineRecord::getCreatedAt)
                .orderByDesc(PerformanceBaselineRecord::getId);
        return performanceMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public List<PerformanceSummaryVo> summary() {
        List<PerformanceBaselineRecord> records = performanceMapper.selectList(new LambdaQueryWrapper<PerformanceBaselineRecord>()
                .orderByDesc(PerformanceBaselineRecord::getCreatedAt));
        Map<String, List<PerformanceBaselineRecord>> byOperation = new LinkedHashMap<>();
        for (PerformanceBaselineRecord record : records) {
            byOperation.computeIfAbsent(record.getOperationType(), ignored -> new ArrayList<>()).add(record);
        }
        List<PerformanceSummaryVo> result = new ArrayList<>();
        for (Map.Entry<String, List<PerformanceBaselineRecord>> entry : byOperation.entrySet()) {
            List<PerformanceBaselineRecord> values = entry.getValue();
            long totalDuration = 0L;
            long maxDuration = 0L;
            long successCount = 0L;
            for (PerformanceBaselineRecord record : values) {
                long duration = record.getDurationMs() == null ? 0L : record.getDurationMs();
                totalDuration += duration;
                maxDuration = Math.max(maxDuration, duration);
                if (Integer.valueOf(1).equals(record.getSuccess())) {
                    successCount++;
                }
            }
            PerformanceSummaryVo vo = new PerformanceSummaryVo();
            vo.setOperationType(entry.getKey());
            vo.setTotalCount((long) values.size());
            vo.setSuccessCount(successCount);
            vo.setFailureCount(values.size() - successCount);
            vo.setAvgDurationMs(values.isEmpty() ? 0L : totalDuration / values.size());
            vo.setMaxDurationMs(maxDuration);
            result.add(vo);
        }
        return result;
    }

    public List<PerformanceTrendVo> trends(String operationType, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<PerformanceBaselineRecord> wrapper = new LambdaQueryWrapper<>();
        if (hasText(operationType)) {
            wrapper.eq(PerformanceBaselineRecord::getOperationType, operationType.trim());
        }
        wrapper.orderByDesc(PerformanceBaselineRecord::getCreatedAt)
                .orderByDesc(PerformanceBaselineRecord::getId)
                .last("LIMIT " + safeLimit);

        List<PerformanceBaselineRecord> records = performanceMapper.selectList(wrapper);
        Map<String, List<PerformanceBaselineRecord>> byOperation = new LinkedHashMap<>();
        for (PerformanceBaselineRecord record : records) {
            byOperation.computeIfAbsent(record.getOperationType(), ignored -> new ArrayList<>()).add(record);
        }

        List<PerformanceTrendVo> result = new ArrayList<>();
        for (List<PerformanceBaselineRecord> values : byOperation.values()) {
            for (int i = 0; i < values.size(); i++) {
                PerformanceBaselineRecord previous = i + 1 < values.size() ? values.get(i + 1) : null;
                result.add(toTrendVo(values.get(i), previous));
            }
        }
        result.sort(Comparator
                .comparing(PerformanceTrendVo::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PerformanceTrendVo::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSafely(String operationType, Long semesterId, Long planId, Long targetId,
                             Integer taskCount, Integer scheduleCount, long durationMs,
                             boolean success, String errorCode, String errorMessage, String extraJson) {
        try {
            PerformanceBaselineRecord record = new PerformanceBaselineRecord();
            record.setOperationType(operationType);
            record.setSemesterId(semesterId);
            record.setPlanId(planId);
            record.setTargetId(targetId);
            record.setTaskCount(taskCount);
            record.setScheduleCount(scheduleCount);
            record.setDurationMs(Math.max(0L, durationMs));
            record.setSuccess(success ? 1 : 0);
            record.setErrorCode(errorCode);
            record.setErrorMessage(errorMessage);
            record.setExtraJson(extraJson);
            record.setCreatedAt(LocalDateTime.now());
            performanceMapper.insert(record);
        } catch (RuntimeException ignored) {
            // 性能统计是旁路治理数据，不能阻塞主业务。
        }
    }

    public static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private PerformanceTrendVo toTrendVo(PerformanceBaselineRecord current, PerformanceBaselineRecord previous) {
        long currentDuration = current.getDurationMs() == null ? 0L : current.getDurationMs();
        Long previousDuration = previous == null ? null : (previous.getDurationMs() == null ? 0L : previous.getDurationMs());

        PerformanceTrendVo vo = new PerformanceTrendVo();
        vo.setId(current.getId());
        vo.setOperationType(current.getOperationType());
        vo.setDurationMs(currentDuration);
        vo.setPreviousDurationMs(previousDuration);
        vo.setDurationDeltaMs(previousDuration == null ? null : currentDuration - previousDuration);
        vo.setDurationChangePercent(changePercent(currentDuration, previousDuration));
        vo.setSlowOperation(currentDuration >= SLOW_OPERATION_THRESHOLD_MS);
        vo.setSlowThresholdMs(SLOW_OPERATION_THRESHOLD_MS);
        vo.setSuccess(current.getSuccess());
        vo.setCreatedAt(current.getCreatedAt());
        return vo;
    }

    private Integer changePercent(long currentDuration, Long previousDuration) {
        if (previousDuration == null || previousDuration == 0L) {
            return null;
        }
        return (int) Math.round((currentDuration - previousDuration) * 100.0 / previousDuration);
    }
}
