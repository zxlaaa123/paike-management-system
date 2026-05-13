package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.AutoScheduleBatch;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.mapper.AutoScheduleBatchMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AutoScheduleBatchService {

    private final AutoScheduleBatchMapper batchMapper;
    private final ScheduleMapper scheduleMapper;

    public Page<AutoScheduleBatch> list(String batchNo, String status, int page, int size) {
        LambdaQueryWrapper<AutoScheduleBatch> wrapper = new LambdaQueryWrapper<>();
        if (batchNo != null && !batchNo.isBlank()) {
            wrapper.like(AutoScheduleBatch::getBatchNo, batchNo);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AutoScheduleBatch::getStatus, status);
        }
        wrapper.orderByDesc(AutoScheduleBatch::getCreateTime);
        return batchMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public AutoScheduleBatch getById(Long id) {
        return batchMapper.selectById(id);
    }

    public AutoScheduleBatch createBatch(int totalTaskCount, boolean clearOldSchedule) {
        AutoScheduleBatch batch = new AutoScheduleBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setTotalTaskCount(totalTaskCount);
        batch.setSuccessTaskCount(0);
        batch.setFailedTaskCount(0);
        batch.setGeneratedScheduleCount(0);
        batch.setClearOldSchedule(clearOldSchedule ? 1 : 0);
        batch.setStatus("RUNNING");
        batch.setStartTime(LocalDateTime.now());
        batch.setCreateTime(LocalDateTime.now());
        batchMapper.insert(batch);
        return batch;
    }

    public void updateBatchResult(Long batchId, int successTaskCount, int failedTaskCount,
                                   int generatedScheduleCount, String status, String message) {
        AutoScheduleBatch batch = batchMapper.selectById(batchId);
        if (batch == null) return;
        batch.setSuccessTaskCount(successTaskCount);
        batch.setFailedTaskCount(failedTaskCount);
        batch.setGeneratedScheduleCount(generatedScheduleCount);
        batch.setStatus(status);
        batch.setMessage(message);
        batch.setEndTime(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    public void deleteBatchSchedules(Long batchId) {
        scheduleMapper.delete(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getBatchId, batchId)
                .eq(Schedule::getSourceType, "AUTO"));
    }

    private String generateBatchNo() {
        return "AUTO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}
