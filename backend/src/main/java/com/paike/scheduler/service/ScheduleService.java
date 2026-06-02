package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.enums.ScheduleSourceType;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.AutoScheduleBatch;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.AutoScheduleBatchMapper;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.paike.scheduler.common.util.StringSanitizer.trimToNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ScheduleConflictService conflictService;
    private final ScheduleLockGuardService lockGuardService;
    private final AutoScheduleBatchMapper autoScheduleBatchMapper;
    private final SemesterService semesterService;

    public Page<Schedule> list(String courseName, String teacherName, String className, String roomName,
            Integer dayOfWeek, Long semesterId, int page, int size) {
        Long resolvedSemesterId = semesterId;
        if (resolvedSemesterId == null) {
            try {
                Semester current = semesterService.getCurrentSemester();
                resolvedSemesterId = current.getId();
            } catch (BusinessException e) {
                log.warn("未找到当前学期，排课列表按业务约定返回空分页，前端显示空列表", e);
                return new Page<>(page, size);
            }
        }

        Page<Schedule> pageResult = scheduleMapper.selectFilteredSchedulePage(
            trimToNull(courseName), trimToNull(teacherName), trimToNull(className), trimToNull(roomName), dayOfWeek,
            resolvedSemesterId, new Page<>(page, size));

        if (!pageResult.getRecords().isEmpty()) {
            fillRelations(pageResult.getRecords());
        }
        return pageResult;
    }

    public Schedule getById(Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null || Integer.valueOf(1).equals(schedule.getDeleted())) {
            throw new BusinessException(404, "排课记录不存在");
        }
        fillRelation(schedule);
        return schedule;
    }

    @Transactional(rollbackFor = Exception.class)
    public Schedule create(Long teachingTaskId, Long timeSlotId, Long classroomId) {
        String conflict = conflictService.checkConflict(teachingTaskId, timeSlotId, classroomId, null);
        if (conflict != null) {
            throw new BusinessException(400, ScheduleConflictService.stripReasonTag(conflict));
        }

        TeachingTask task = teachingTaskMapper.selectById(teachingTaskId);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new BusinessException(400, "教学任务不存在或已删除");
        }

        Schedule schedule = new Schedule();
        schedule.setSemesterId(task.getSemesterId());
        schedule.setTeachingTaskId(teachingTaskId);
        schedule.setCourseId(task.getCourseId());
        schedule.setTeacherId(task.getTeacherId());
        schedule.setClassId(task.getClassId());
        schedule.setTimeSlotId(timeSlotId);
        schedule.setClassroomId(classroomId);
        schedule.setSourceType(ScheduleSourceType.MANUAL.getCode());
        schedule.setDeleted(0);
        schedule.setCreateTime(LocalDateTime.now());
        schedule.setUpdateTime(LocalDateTime.now());
        try {
            scheduleMapper.insert(schedule);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409, "排课冲突：该时间段已有其他课程占用，请刷新后重试");
        }

        fillRelation(schedule);
        return schedule;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null || Integer.valueOf(1).equals(schedule.getDeleted())) {
            throw new BusinessException(404, "排课记录不存在");
        }
        lockGuardService.ensureScheduleAndLinkedPlanUnlocked(schedule, "该课程已锁定，不能删除");
        scheduleMapper.deleteById(id);
    }

    /** 按班级查询排课列表 */
    public List<Schedule> listByClass(Long classId) {
        List<TeachingTask> tasks = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getClassId, classId)
        );
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Long> taskIds = tasks.stream().map(TeachingTask::getId).collect(Collectors.toList());
        List<Schedule> list = scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .in(Schedule::getTeachingTaskId, taskIds)
        );
        fillRelations(list);
        return list;
    }

    /** 按教师查询排课列表 */
    public List<Schedule> listByTeacher(Long teacherId) {
        List<TeachingTask> tasks = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getTeacherId, teacherId)
        );
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Long> taskIds = tasks.stream().map(TeachingTask::getId).collect(Collectors.toList());
        List<Schedule> list = scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .in(Schedule::getTeachingTaskId, taskIds)
        );
        fillRelations(list);
        return list;
    }

    /** 按教室查询排课列表 */
    public List<Schedule> listByClassroom(Long classroomId) {
        List<Schedule> list = scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getClassroomId, classroomId)
        );
        fillRelations(list);
        return list;
    }

    public String checkConflict(Long teachingTaskId, Long timeSlotId, Long classroomId) {
        String conflict = conflictService.checkConflict(teachingTaskId, timeSlotId, classroomId, null);
        if (conflict != null) {
            return ScheduleConflictService.stripReasonTag(conflict);
        }
        return null;
    }

    private void fillRelations(List<Schedule> list) {
        if (list.isEmpty()) {
            return;
        }

        List<Long> timeSlotIds = list.stream().map(Schedule::getTimeSlotId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> classroomIds = list.stream().map(Schedule::getClassroomId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> taskIds = list.stream().map(Schedule::getTeachingTaskId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> batchIds = list.stream().map(Schedule::getBatchId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Map<Long, TimeSlot> timeSlotMap = timeSlotIds.isEmpty() ? Map.of() :
            timeSlotMapper.selectBatchIds(timeSlotIds).stream().collect(Collectors.toMap(TimeSlot::getId, Function.identity(), (a, b) -> a));
        Map<Long, Classroom> classroomMap = classroomIds.isEmpty() ? Map.of() :
            classroomMapper.selectBatchIds(classroomIds).stream().collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));
        Map<Long, TeachingTask> taskMap = taskIds.isEmpty() ? Map.of() :
            teachingTaskMapper.selectBatchIds(taskIds).stream().collect(Collectors.toMap(TeachingTask::getId, Function.identity(), (a, b) -> a));
        Map<Long, AutoScheduleBatch> batchMap = batchIds.isEmpty() ? Map.of() :
            autoScheduleBatchMapper.selectBatchIds(batchIds).stream().collect(Collectors.toMap(AutoScheduleBatch::getId, Function.identity(), (a, b) -> a));

        List<Long> courseIds = new ArrayList<>();
        List<Long> teacherIds = new ArrayList<>();
        List<Long> classIds = new ArrayList<>();
        for (TeachingTask task : taskMap.values()) {
            if (task.getCourseId() != null) {
                courseIds.add(task.getCourseId());
            }
            if (task.getTeacherId() != null) {
                teacherIds.add(task.getTeacherId());
            }
            if (task.getClassId() != null) {
                classIds.add(task.getClassId());
            }
        }

        Map<Long, Course> courseMap = courseIds.isEmpty() ? Map.of() :
            courseMapper.selectBatchIds(courseIds).stream().collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherIds.isEmpty() ? Map.of() :
            teacherMapper.selectBatchIds(teacherIds).stream().collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        Map<Long, ClassInfo> classMap = classIds.isEmpty() ? Map.of() :
            classInfoMapper.selectBatchIds(classIds).stream().collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));

        for (Schedule schedule : list) {
            TimeSlot timeSlot = timeSlotMap.get(schedule.getTimeSlotId());
            if (timeSlot != null) {
                schedule.setTimeLabel(timeSlot.getTimeLabel());
                schedule.setDayOfWeek(timeSlot.getDayOfWeek());
                schedule.setPeriodNo(timeSlot.getPeriodNo());
            }
            Classroom classroom = classroomMap.get(schedule.getClassroomId());
            if (classroom != null) {
                schedule.setRoomName(classroom.getRoomName());
                schedule.setBuilding(classroom.getBuilding());
            }
            TeachingTask task = taskMap.get(schedule.getTeachingTaskId());
            if (task != null) {
                Course course = courseMap.get(task.getCourseId());
                if (course != null) {
                    schedule.setCourseName(course.getCourseName());
                }
                Teacher teacher = teacherMap.get(task.getTeacherId());
                if (teacher != null) {
                    schedule.setTeacherName(teacher.getName());
                }
                ClassInfo classInfo = classMap.get(task.getClassId());
                if (classInfo != null) {
                    schedule.setClassName(classInfo.getClassName());
                }
            }
            if (schedule.getSourceType() != null) {
                schedule.setSourceTypeName(ScheduleSourceType.AUTO.getCode().equals(schedule.getSourceType()) ? "自动排课" : "手动排课");
            } else {
                schedule.setSourceTypeName("手动排课");
            }
            if (schedule.getBatchId() != null) {
                AutoScheduleBatch batch = batchMap.get(schedule.getBatchId());
                if (batch != null) {
                    schedule.setBatchNo(batch.getBatchNo());
                }
            }
        }
    }

    private void fillRelation(Schedule schedule) {
        fillRelations(List.of(schedule));
    }
}
