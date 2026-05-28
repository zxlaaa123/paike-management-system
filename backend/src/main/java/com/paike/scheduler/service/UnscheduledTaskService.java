package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UnscheduledTaskService {

    private final UnscheduledTaskMapper unscheduledTaskMapper;
    private final TeachingTaskMapper teachingTaskMapper;

    public Page<UnscheduledTask> list(Long batchId, String courseName, String teacherName,
                                       String className, String reasonType, int page, int size) {
        return unscheduledTaskMapper.selectFilteredPage(
                batchId,
                filterOrNull(courseName),
                filterOrNull(teacherName),
                filterOrNull(className),
                filterOrNull(reasonType),
                new Page<>(page, size));
    }

    public void addUnscheduledTask(Long batchId, Long semesterId, Long taskId, int requiredSlots,
                                    int scheduledSlots, int remainingSlots,
                                    String reasonType, String reasonMessage) {
        TeachingTask task = teachingTaskMapper.selectById(taskId);
        if (task == null) return;

        UnscheduledTask ut = new UnscheduledTask();
        ut.setBatchId(batchId);
        ut.setSemesterId(semesterId);
        ut.setTaskId(taskId);
        ut.setCourseId(task.getCourseId());
        ut.setTeacherId(task.getTeacherId());
        ut.setClassId(task.getClassId());
        ut.setRequiredSlots(requiredSlots);
        ut.setScheduledSlots(scheduledSlots);
        ut.setRemainingSlots(remainingSlots);
        ut.setReasonType(reasonType);
        ut.setReasonMessage(reasonMessage);
        ut.setCreateTime(LocalDateTime.now());
        unscheduledTaskMapper.insert(ut);
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearByBatchId(Long batchId) {
        unscheduledTaskMapper.delete(new LambdaQueryWrapper<UnscheduledTask>()
                .eq(UnscheduledTask::getBatchId, batchId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearBySemester(Long semesterId) {
        if (semesterId == null) {
            throw new BusinessException("clearBySemester 必须传入 semesterId");
        }
        unscheduledTaskMapper.delete(new LambdaQueryWrapper<UnscheduledTask>()
                .eq(UnscheduledTask::getSemesterId, semesterId));
    }

    private String filterOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
