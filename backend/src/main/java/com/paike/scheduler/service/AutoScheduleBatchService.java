package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.AutoScheduleBatch;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.mapper.AutoScheduleBatchMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AutoScheduleBatchService {

    private static final int BATCH_NO_MAX_ATTEMPTS = 5;

    private final AutoScheduleBatchMapper batchMapper;
    private final ScheduleMapper scheduleMapper;

    public Page<AutoScheduleBatch> list(Long semesterId, String batchNo, String status, int page, int size) {
        LambdaQueryWrapper<AutoScheduleBatch> wrapper = new LambdaQueryWrapper<>();
        if (semesterId != null) {
            wrapper.eq(AutoScheduleBatch::getSemesterId, semesterId);
        }
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

    public AutoScheduleBatch createBatch(Long semesterId, int totalTaskCount, boolean clearOldSchedule) {
        for (int attempt = 1; attempt <= BATCH_NO_MAX_ATTEMPTS; attempt++) {
            AutoScheduleBatch batch = buildBatch(semesterId, totalTaskCount, clearOldSchedule);
            try {
                batchMapper.insert(batch);
                return batch;
            } catch (DuplicateKeyException ex) {
                if (attempt == BATCH_NO_MAX_ATTEMPTS) {
                    throw new BusinessException("自动排课批次号生成失败，请重试");
                }
            }
        }
        throw new BusinessException("自动排课批次号生成失败，请重试");
    }

    private AutoScheduleBatch buildBatch(Long semesterId, int totalTaskCount, boolean clearOldSchedule) {
        LocalDateTime now = LocalDateTime.now();
        AutoScheduleBatch batch = new AutoScheduleBatch();
        batch.setSemesterId(semesterId);
        batch.setBatchNo(generateBatchNo());
        batch.setTotalTaskCount(totalTaskCount);
        batch.setSuccessTaskCount(0);
        batch.setFailedTaskCount(0);
        batch.setGeneratedScheduleCount(0);
        batch.setClearOldSchedule(clearOldSchedule ? 1 : 0);
        batch.setStatus("RUNNING");
        batch.setStartTime(now);
        batch.setCreateTime(now);
        return batch;
    }

    @Transactional(rollbackFor = Exception.class)
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
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "AUTO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + random;
    }
}
