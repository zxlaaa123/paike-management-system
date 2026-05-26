package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulePlanExplainService {

    private static final Map<String, String> REASON_NAME_MAP = Map.of(
            "NO_AVAILABLE_CLASSROOM", "无可用教室",
            "TEACHER_TIME_CONFLICT", "教师时间冲突",
            "CLASS_TIME_CONFLICT", "班级时间冲突",
            "CLASSROOM_TYPE_MISMATCH", "教室类型不匹配",
            "CLASSROOM_CAPACITY_NOT_ENOUGH", "教室容量不足",
            "TEACHER_UNAVAILABLE", "教师禁排时间冲突",
            "PERIOD_NOT_ENOUGH", "可用节次不足",
            "UNKNOWN_REASON", "未知原因"
    );

    private final ScheduleGenerateLogMapper generateLogMapper;
    private final ScheduleUnassignedTaskMapper unassignedTaskMapper;
    private final ScheduleAdjustLogMapper adjustLogMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;

    @Transactional(rollbackFor = Exception.class)
    public void clearPlanArtifacts(Long planId) {
        generateLogMapper.delete(new LambdaQueryWrapper<ScheduleGenerateLog>()
                .eq(ScheduleGenerateLog::getPlanId, planId));
        unassignedTaskMapper.delete(new LambdaQueryWrapper<ScheduleUnassignedTask>()
                .eq(ScheduleUnassignedTask::getPlanId, planId));
        adjustLogMapper.delete(new LambdaQueryWrapper<ScheduleAdjustLog>()
                .eq(ScheduleAdjustLog::getPlanId, planId));
    }

    public void appendGenerateLog(Long planId, Long semesterId, Long taskId, String level, String type, String message, Integer stepNo) {
        // 验收阶段降压策略：仅持久化 ERROR 日志，避免大量 INFO/WARN 写入导致锁竞争并阻塞主流程。
        if (!"ERROR".equalsIgnoreCase(level)) {
            return;
        }
        int maxRetry = 3;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                ScheduleGenerateLog log = new ScheduleGenerateLog();
                log.setPlanId(planId);
                log.setSemesterId(semesterId);
                log.setTeachingTaskId(taskId);
                log.setLogLevel(level);
                log.setLogType(type);
                log.setMessage(message);
                log.setStepNo(stepNo);
                log.setCreatedAt(LocalDateTime.now());
                generateLogMapper.insert(log);
                return;
            } catch (PessimisticLockingFailureException ex) {
                if (attempt >= maxRetry) {
                    // 生成日志失败不应阻塞排课主链路，记录后降级忽略。
                    log.warn("appendGenerateLog deadlock dropped: planId={}, type={}, stepNo={}, message={}",
                            planId, type, stepNo, message, ex);
                    return;
                }
                sleepQuietly(20L * attempt);
            }
        }
    }

    public List<ScheduleGenerateLog> listPlanLogs(Long planId, String logLevel, String logType, Long teachingTaskId) {
        LambdaQueryWrapper<ScheduleGenerateLog> wrapper = new LambdaQueryWrapper<ScheduleGenerateLog>()
                .eq(ScheduleGenerateLog::getPlanId, planId);
        if (logLevel != null && !logLevel.isBlank()) {
            wrapper.eq(ScheduleGenerateLog::getLogLevel, logLevel.trim());
        }
        if (logType != null && !logType.isBlank()) {
            wrapper.eq(ScheduleGenerateLog::getLogType, logType.trim());
        }
        if (teachingTaskId != null) {
            wrapper.eq(ScheduleGenerateLog::getTeachingTaskId, teachingTaskId);
        }
        wrapper.orderByAsc(ScheduleGenerateLog::getStepNo)
                .orderByAsc(ScheduleGenerateLog::getCreatedAt);
        return generateLogMapper.selectList(wrapper);
    }

    public List<ScheduleGenerateLog> listTaskLogs(Long planId, Long taskId) {
        return listPlanLogs(planId, null, null, taskId);
    }

    public void saveUnassignedTask(Long planId, Long semesterId, Long taskId, String reasonCode, String reasonMessage, String suggestion) {
        ScheduleUnassignedTask record = new ScheduleUnassignedTask();
        record.setPlanId(planId);
        record.setSemesterId(semesterId);
        record.setTeachingTaskId(taskId);
        record.setReasonCode(reasonCode);
        record.setReasonMessage(reasonMessage);
        record.setSuggestion(suggestion);
        record.setCreatedAt(LocalDateTime.now());
        unassignedTaskMapper.insert(record);
    }

    public List<ScheduleUnassignedTask> listUnassignedTasks(Long planId) {
        List<ScheduleUnassignedTask> records = unassignedTaskMapper.selectList(
                new LambdaQueryWrapper<ScheduleUnassignedTask>()
                        .eq(ScheduleUnassignedTask::getPlanId, planId)
                        .orderByDesc(ScheduleUnassignedTask::getCreatedAt));
        fillUnassignedRelations(records);
        return records;
    }

    public List<Map<String, Object>> summarizeUnassignedTasks(Long planId) {
        List<ScheduleUnassignedTask> records = unassignedTaskMapper.selectList(
                new LambdaQueryWrapper<ScheduleUnassignedTask>().eq(ScheduleUnassignedTask::getPlanId, planId));
        return records.stream()
                .collect(Collectors.groupingBy(ScheduleUnassignedTask::getReasonCode, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("reasonCode", entry.getKey());
                    item.put("reasonName", reasonName(entry.getKey()));
                    item.put("count", entry.getValue());
                    return item;
                })
                .toList();
    }

    public void appendAdjustLog(ScheduleAdjustLog log) {
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(LocalDateTime.now());
        }
        adjustLogMapper.insert(log);
    }

    public Page<ScheduleAdjustLog> listAdjustLogs(Long semesterId, Long planId, Long teachingTaskId, int pageNum, int pageSize) {
        LambdaQueryWrapper<ScheduleAdjustLog> wrapper = new LambdaQueryWrapper<ScheduleAdjustLog>()
                .orderByDesc(ScheduleAdjustLog::getCreatedAt);
        if (semesterId != null) {
            wrapper.eq(ScheduleAdjustLog::getSemesterId, semesterId);
        }
        if (planId != null) {
            wrapper.eq(ScheduleAdjustLog::getPlanId, planId);
        }
        if (teachingTaskId != null) {
            wrapper.eq(ScheduleAdjustLog::getTeachingTaskId, teachingTaskId);
        }
        Page<ScheduleAdjustLog> page = adjustLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillAdjustRelations(page.getRecords());
        return page;
    }

    public String reasonName(String reasonCode) {
        return REASON_NAME_MAP.getOrDefault(reasonCode, reasonCode);
    }

    private void fillUnassignedRelations(List<ScheduleUnassignedTask> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, TeachingTask> taskMap = teachingTaskMapper.selectBatchIds(records.stream()
                        .map(ScheduleUnassignedTask::getTeachingTaskId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(TeachingTask::getId, Function.identity(), (a, b) -> a));

        Map<Long, Course> courseMap = courseMapper.selectBatchIds(taskMap.values().stream()
                        .map(TeachingTask::getCourseId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherMapper.selectBatchIds(taskMap.values().stream()
                        .map(TeachingTask::getTeacherId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        Map<Long, ClassInfo> classMap = classInfoMapper.selectBatchIds(taskMap.values().stream()
                        .map(TeachingTask::getClassId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));

        for (ScheduleUnassignedTask record : records) {
            TeachingTask task = taskMap.get(record.getTeachingTaskId());
            if (task == null) {
                continue;
            }
            Course course = courseMap.get(task.getCourseId());
            Teacher teacher = teacherMap.get(task.getTeacherId());
            ClassInfo classInfo = classMap.get(task.getClassId());
            record.setCourseName(course != null ? course.getCourseName() : null);
            record.setTeacherName(teacher != null ? teacher.getName() : null);
            record.setClassName(classInfo != null ? classInfo.getClassName() : null);
        }
    }

    private void fillAdjustRelations(List<ScheduleAdjustLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, TeachingTask> taskMap = teachingTaskMapper.selectBatchIds(records.stream()
                        .map(ScheduleAdjustLog::getTeachingTaskId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(TeachingTask::getId, Function.identity(), (a, b) -> a));
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(taskMap.values().stream()
                        .map(TeachingTask::getCourseId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherMapper.selectBatchIds(taskMap.values().stream()
                        .map(TeachingTask::getTeacherId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        Map<Long, ClassInfo> classMap = classInfoMapper.selectBatchIds(taskMap.values().stream()
                        .map(TeachingTask::getClassId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));
        Set<Long> roomIds = new HashSet<>();
        for (ScheduleAdjustLog record : records) {
            if (record.getOldClassroomId() != null) {
                roomIds.add(record.getOldClassroomId());
            }
            if (record.getNewClassroomId() != null) {
                roomIds.add(record.getNewClassroomId());
            }
        }
        Map<Long, Classroom> roomMap = roomIds.isEmpty()
                ? Map.of()
                : classroomMapper.selectBatchIds(roomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));

        for (ScheduleAdjustLog record : records) {
            TeachingTask task = taskMap.get(record.getTeachingTaskId());
            if (task != null) {
                Course course = courseMap.get(task.getCourseId());
                Teacher teacher = teacherMap.get(task.getTeacherId());
                ClassInfo classInfo = classMap.get(task.getClassId());
                record.setCourseName(course != null ? course.getCourseName() : null);
                record.setTeacherName(teacher != null ? teacher.getName() : null);
                record.setClassName(classInfo != null ? classInfo.getClassName() : null);
            }
            Classroom oldRoom = roomMap.get(record.getOldClassroomId());
            Classroom newRoom = roomMap.get(record.getNewClassroomId());
            record.setOldClassroomName(oldRoom != null ? oldRoom.getRoomName() : null);
            record.setNewClassroomName(newRoom != null ? newRoom.getRoomName() : null);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
