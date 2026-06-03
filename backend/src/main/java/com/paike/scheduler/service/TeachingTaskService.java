package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.service.vo.TeachingTaskVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeachingTaskService {

    private final TeachingTaskMapper teachingTaskMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ScheduleMapper scheduleMapper;
    private final SemesterService semesterService;

    public Page<TeachingTaskVo> list(String courseName, String teacherName, String className, Integer status,
            Long semesterId, int pageNum, int pageSize) {
        Long resolvedSemesterId = semesterId;
        if (resolvedSemesterId == null) {
            try {
                Semester current = semesterService.getCurrentSemester();
                resolvedSemesterId = current.getId();
            } catch (BusinessException e) {
                log.warn("未找到当前学期，教学任务列表按业务约定返回空分页，前端显示空列表", e);
                return new Page<>(pageNum, pageSize);
            }
        }

        Page<TeachingTask> pageResult = new Page<>(pageNum, pageSize);
        List<TeachingTask> records = teachingTaskMapper.selectFilteredTasks(
            courseName, teacherName, className, status, resolvedSemesterId, pageResult);
        List<TeachingTaskVo> vos = records.stream().map(TeachingTaskVo::fromEntity).collect(Collectors.toList());

        if (!vos.isEmpty()) {
            fillTaskRelations(vos);
        }
        Page<TeachingTaskVo> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    public TeachingTaskVo getById(Long id) {
        TeachingTask task = teachingTaskMapper.selectById(id);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new BusinessException(404, "教学任务不存在");
        }
        TeachingTaskVo vo = TeachingTaskVo.fromEntity(task);
        fillTaskRelations(List.of(vo));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public TeachingTaskVo create(Long courseId, Long teacherId, Long classId, Integer weeklyHours, Integer needContinuous,
            Integer status, String remark) {
        Course course = requireActiveCourse(courseId);
        Teacher teacher = requireActiveTeacher(teacherId);
        ClassInfo classInfo = requireActiveClass(classId);

        TeachingTask task = new TeachingTask();
        task.setSemesterId(semesterService.getCurrentSemester().getId());
        task.setCourseId(courseId);
        task.setTeacherId(teacherId);
        task.setClassId(classId);
        task.setWeeklyHours(weeklyHours);
        task.setNeedContinuous(needContinuous != null ? needContinuous : 0);
        task.setStatus(status != null ? status : 1);
        task.setRemark(remark);
        task.setDeleted(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        teachingTaskMapper.insert(task);

        TeachingTaskVo vo = TeachingTaskVo.fromEntity(task);
        vo.setCourseName(course.getCourseName());
        vo.setTeacherName(teacher.getName());
        vo.setClassName(classInfo.getClassName());
        vo.setScheduledSlots(0);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public TeachingTaskVo update(Long id, Long courseId, Long teacherId, Long classId, Integer weeklyHours,
            Integer needContinuous, Integer status, String remark) {
        TeachingTask task = teachingTaskMapper.selectById(id);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new BusinessException(404, "教学任务不存在");
        }

        Course course = requireActiveCourse(courseId);
        Teacher teacher = requireActiveTeacher(teacherId);
        ClassInfo classInfo = requireActiveClass(classId);

        task.setCourseId(courseId);
        task.setTeacherId(teacherId);
        task.setClassId(classId);
        task.setWeeklyHours(weeklyHours);
        task.setNeedContinuous(needContinuous != null ? needContinuous : 0);
        if (status != null) {
            task.setStatus(status);
        }
        task.setRemark(remark);
        task.setUpdateTime(LocalDateTime.now());
        teachingTaskMapper.updateById(task);

        TeachingTaskVo vo = TeachingTaskVo.fromEntity(task);
        vo.setCourseName(course.getCourseName());
        vo.setTeacherName(teacher.getName());
        vo.setClassName(classInfo.getClassName());
        Long count = scheduleMapper.selectCount(
            new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getTeachingTaskId, task.getId()));
        vo.setScheduledSlots(count != null ? count.intValue() : 0);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TeachingTask task = teachingTaskMapper.selectById(id);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new BusinessException(404, "教学任务不存在");
        }
        teachingTaskMapper.deleteById(id);
    }

    public List<TeachingTaskVo> listAll() {
        List<TeachingTask> list = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getStatus, 1)
                .orderByDesc(TeachingTask::getCreateTime)
        );
        List<TeachingTaskVo> vos = list.stream().map(TeachingTaskVo::fromEntity).collect(Collectors.toList());
        fillTaskRelations(vos);
        return vos;
    }

    /** 批量填充教学任务关联数据（避免 N+1 查询） */
    private void fillTaskRelations(List<TeachingTaskVo> tasks) {
        if (tasks.isEmpty()) {
            return;
        }

        List<Long> courseIds = tasks.stream().map(TeachingTaskVo::getCourseId).distinct().collect(Collectors.toList());
        List<Long> teacherIds = tasks.stream().map(TeachingTaskVo::getTeacherId).distinct().collect(Collectors.toList());
        List<Long> classIds = tasks.stream().map(TeachingTaskVo::getClassId).distinct().collect(Collectors.toList());
        List<Long> taskIds = tasks.stream().map(TeachingTaskVo::getId).distinct().collect(Collectors.toList());

        Map<Long, String> courseNameMap = courseIds.isEmpty() ? Map.of() :
            courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName));
        Map<Long, String> teacherNameMap = teacherIds.isEmpty() ? Map.of() :
            teacherMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Teacher::getName));
        Map<Long, String> classNameMap = classIds.isEmpty() ? Map.of() :
            classInfoMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassInfo::getId, ClassInfo::getClassName));
        Map<Long, Long> scheduledCountMap = taskIds.isEmpty() ? Map.of() :
            scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                    .in(Schedule::getTeachingTaskId, taskIds))
                .stream()
                .collect(Collectors.groupingBy(Schedule::getTeachingTaskId, Collectors.counting()));

        for (TeachingTaskVo task : tasks) {
            task.setCourseName(courseNameMap.get(task.getCourseId()));
            task.setTeacherName(teacherNameMap.get(task.getTeacherId()));
            task.setClassName(classNameMap.get(task.getClassId()));
            task.setScheduledSlots(scheduledCountMap.getOrDefault(task.getId(), 0L).intValue());
        }
    }

    private Course requireActiveCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || Integer.valueOf(1).equals(course.getDeleted())) {
            throw new BusinessException(400, "所选课程不存在");
        }
        return course;
    }

    private Teacher requireActiveTeacher(Long teacherId) {
        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || Integer.valueOf(1).equals(teacher.getDeleted())) {
            throw new BusinessException(400, "所选教师不存在");
        }
        if (teacher.getStatus() != 1) {
            throw new BusinessException(400, "所选教师已停用，无法创建教学任务");
        }
        return teacher;
    }

    private ClassInfo requireActiveClass(Long classId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null || Integer.valueOf(1).equals(classInfo.getDeleted())) {
            throw new BusinessException(400, "所选班级不存在");
        }
        if (classInfo.getStatus() != 1) {
            throw new BusinessException(400, "所选班级已停用，无法创建教学任务");
        }
        return classInfo;
    }
}
