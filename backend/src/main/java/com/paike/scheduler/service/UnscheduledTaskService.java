package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnscheduledTaskService {

    private final UnscheduledTaskMapper unscheduledTaskMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final AutoScheduleBatchMapper batchMapper;

    public Page<UnscheduledTask> list(Long batchId, String courseName, String teacherName,
                                       String className, String reasonType, int page, int size) {
        LambdaQueryWrapper<UnscheduledTask> wrapper = new LambdaQueryWrapper<>();
        if (batchId != null) {
            wrapper.eq(UnscheduledTask::getBatchId, batchId);
        }
        if (reasonType != null && !reasonType.isBlank()) {
            wrapper.eq(UnscheduledTask::getReasonType, reasonType);
        }
        wrapper.orderByDesc(UnscheduledTask::getCreateTime);
        Page<UnscheduledTask> result = unscheduledTaskMapper.selectPage(new Page<>(page, size), wrapper);

        // 内存过滤关联字段
        if (courseName != null && !courseName.isBlank()) {
            result.setRecords(result.getRecords().stream()
                    .filter(t -> t.getCourseName() != null && t.getCourseName().contains(courseName))
                    .collect(Collectors.toList()));
        }
        if (teacherName != null && !teacherName.isBlank()) {
            result.setRecords(result.getRecords().stream()
                    .filter(t -> t.getTeacherName() != null && t.getTeacherName().contains(teacherName))
                    .collect(Collectors.toList()));
        }
        if (className != null && !className.isBlank()) {
            result.setRecords(result.getRecords().stream()
                    .filter(t -> t.getClassName() != null && t.getClassName().contains(className))
                    .collect(Collectors.toList()));
        }

        fillRelationFields(result.getRecords());
        return result;
    }

    public void addUnscheduledTask(Long batchId, Long taskId, int requiredSlots,
                                    int scheduledSlots, int remainingSlots,
                                    String reasonType, String reasonMessage) {
        TeachingTask task = teachingTaskMapper.selectById(taskId);
        if (task == null) return;

        UnscheduledTask ut = new UnscheduledTask();
        ut.setBatchId(batchId);
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

    public void clearByBatchId(Long batchId) {
        unscheduledTaskMapper.delete(new LambdaQueryWrapper<UnscheduledTask>()
                .eq(UnscheduledTask::getBatchId, batchId));
    }

    public void clearAll() {
        unscheduledTaskMapper.delete(new LambdaQueryWrapper<>());
    }

    private void fillRelationFields(List<UnscheduledTask> records) {
        if (records.isEmpty()) return;

        List<Long> taskIds = records.stream().map(UnscheduledTask::getTaskId).distinct().collect(Collectors.toList());
        List<Long> batchIds = records.stream().map(UnscheduledTask::getBatchId).distinct().collect(Collectors.toList());

        Map<Long, TeachingTask> taskMap = teachingTaskMapper.selectList(
                        new LambdaQueryWrapper<TeachingTask>().in(TeachingTask::getId, taskIds)).stream()
                .collect(Collectors.toMap(TeachingTask::getId, t -> t, (a, b) -> a));

        Map<Long, AutoScheduleBatch> batchMap = batchMapper.selectList(
                        new LambdaQueryWrapper<AutoScheduleBatch>().in(AutoScheduleBatch::getId, batchIds)).stream()
                .collect(Collectors.toMap(AutoScheduleBatch::getId, b -> b, (a, b) -> a));

        for (UnscheduledTask ut : records) {
            TeachingTask task = taskMap.get(ut.getTaskId());
            if (task != null) {
                Course course = courseMapper.selectById(task.getCourseId());
                if (course != null) ut.setCourseName(course.getCourseName());
                Teacher teacher = teacherMapper.selectById(task.getTeacherId());
                if (teacher != null) ut.setTeacherName(teacher.getName());
                ClassInfo classInfo = classInfoMapper.selectById(task.getClassId());
                if (classInfo != null) ut.setClassName(classInfo.getClassName());
            }
            AutoScheduleBatch batch = batchMap.get(ut.getBatchId());
            if (batch != null) ut.setBatchNo(batch.getBatchNo());
        }
    }
}
