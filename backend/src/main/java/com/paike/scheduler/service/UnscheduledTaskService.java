package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // courseName/teacherName/className 需要通过关联表过滤，先查全量再内存过滤
        // 但 batchId 和 reasonType 可以在数据库层面过滤
        LambdaQueryWrapper<UnscheduledTask> wrapper = new LambdaQueryWrapper<>();
        if (batchId != null) {
            wrapper.eq(UnscheduledTask::getBatchId, batchId);
        }
        if (reasonType != null && !reasonType.isBlank()) {
            wrapper.eq(UnscheduledTask::getReasonType, reasonType);
        }
        wrapper.orderByDesc(UnscheduledTask::getCreateTime);

        // 当没有关联表过滤条件时，使用数据库分页
        boolean hasRelationFilter = (courseName != null && !courseName.isBlank())
            || (teacherName != null && !teacherName.isBlank())
            || (className != null && !className.isBlank());

        if (!hasRelationFilter) {
            // 无关联过滤条件，直接数据库分页
            Page<UnscheduledTask> pageResult = unscheduledTaskMapper.selectPage(new Page<>(page, size), wrapper);
            fillRelationFields(pageResult.getRecords());
            return pageResult;
        }

        // 有关联过滤条件，需要内存过滤（待排任务数据量通常不大）
        List<UnscheduledTask> allRecords = unscheduledTaskMapper.selectList(wrapper);
        fillRelationFields(allRecords);

        List<UnscheduledTask> filtered = allRecords;
        if (courseName != null && !courseName.isBlank()) {
            filtered = filtered.stream()
                    .filter(t -> t.getCourseName() != null && t.getCourseName().contains(courseName))
                    .collect(Collectors.toList());
        }
        if (teacherName != null && !teacherName.isBlank()) {
            filtered = filtered.stream()
                    .filter(t -> t.getTeacherName() != null && t.getTeacherName().contains(teacherName))
                    .collect(Collectors.toList());
        }
        if (className != null && !className.isBlank()) {
            filtered = filtered.stream()
                    .filter(t -> t.getClassName() != null && t.getClassName().contains(className))
                    .collect(Collectors.toList());
        }

        int total = filtered.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<UnscheduledTask> pageRecords = fromIndex < total ? filtered.subList(fromIndex, toIndex) : List.of();

        Page<UnscheduledTask> pageResult = new Page<>(page, size);
        pageResult.setRecords(pageRecords);
        pageResult.setTotal(total);
        return pageResult;
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

    @Transactional(rollbackFor = Exception.class)
    public void clearByBatchId(Long batchId) {
        unscheduledTaskMapper.delete(new LambdaQueryWrapper<UnscheduledTask>()
                .eq(UnscheduledTask::getBatchId, batchId));
    }

    @Transactional(rollbackFor = Exception.class)
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
