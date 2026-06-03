package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.vo.ScheduleConflictReportVo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleConflictReportService {

    private static final DateTimeFormatter REPORT_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Map<Integer, String> WEEKDAY_TEXT = Map.of(
            1, "周一",
            2, "周二",
            3, "周三",
            4, "周四",
            5, "周五",
            6, "周六",
            7, "周日"
    );

    private final ScheduleConflictReportMapper conflictReportMapper;
    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TeacherUnavailableTimeMapper unavailableTimeMapper;
    private final ScheduleRuleService ruleService;
    private final SemesterService semesterService;

    public Page<ScheduleConflictReportVo> list(Long semesterId, String reportNo, String conflictType,
                                               String objectType, String objectName, int page, int size) {
        Long resolvedSemesterId = resolveSemesterId(semesterId);
        LambdaQueryWrapper<ScheduleConflictReport> wrapper = new LambdaQueryWrapper<ScheduleConflictReport>()
                .eq(ScheduleConflictReport::getSemesterId, resolvedSemesterId)
                .orderByDesc(ScheduleConflictReport::getCreateTime)
                .orderByDesc(ScheduleConflictReport::getId);
        if (reportNo != null && !reportNo.isBlank()) {
            wrapper.like(ScheduleConflictReport::getReportNo, reportNo.trim());
        }
        if (conflictType != null && !conflictType.isBlank()) {
            wrapper.eq(ScheduleConflictReport::getConflictType, conflictType.trim());
        }
        if (objectType != null && !objectType.isBlank()) {
            wrapper.eq(ScheduleConflictReport::getObjectType, objectType.trim());
        }
        if (objectName != null && !objectName.isBlank()) {
            wrapper.like(ScheduleConflictReport::getObjectName, objectName.trim());
        }

        Page<ScheduleConflictReport> result = conflictReportMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ScheduleConflictReportVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVo).collect(Collectors.toList()));
        fillRelationFields(voPage.getRecords());
        return voPage;
    }

    @Transactional(rollbackFor = Exception.class)
    public GenerateResult generate() {
        return generate(null);
    }

    @Transactional(rollbackFor = Exception.class)
    public GenerateResult generate(Long semesterId) {
        Long resolvedSemesterId = resolveSemesterId(semesterId);
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, resolvedSemesterId));

        String baseReportNo = "CR" + REPORT_NO_TIME_FORMATTER.format(LocalDateTime.now());

        Context context = buildContext(schedules, resolvedSemesterId);
        List<ScheduleConflictReport> reports = new ArrayList<>();

        detectTeacherConflicts(baseReportNo, context, reports);
        detectClassConflicts(baseReportNo, context, reports);
        detectClassroomConflicts(baseReportNo, context, reports);
        detectCapacityIssues(baseReportNo, context, reports);
        detectRoomTypeMismatch(baseReportNo, context, reports);
        detectTeacherUnavailableIssues(baseReportNo, context, reports);
        detectUnfinishedTasks(baseReportNo, context, reports);
        detectTeacherDailyOverload(baseReportNo, context, reports);
        detectClassDailyOverload(baseReportNo, context, reports);

        for (ScheduleConflictReport report : reports) {
            report.setSemesterId(resolvedSemesterId);
            conflictReportMapper.insert(report);
        }

        String message = reports.isEmpty()
                ? "检测完成，当前课表未发现冲突问题"
                : "检测完成，发现 " + reports.size() + " 条问题";
        return new GenerateResult(baseReportNo, reports.size(), message);
    }

    @Transactional(rollbackFor = Exception.class)
    public void clear(String reportNo) {
        if (reportNo == null || reportNo.isBlank()) {
            throw new BusinessException(400, "报告编号不能为空");
        }
        LambdaQueryWrapper<ScheduleConflictReport> wrapper = new LambdaQueryWrapper<>();
        String normalized = normalizeReportNo(reportNo.trim());
        wrapper.and(w -> w.eq(ScheduleConflictReport::getReportNo, normalized)
                .or()
                .apply("report_no REGEXP {0}", "^" + escapeRegex(normalized) + "-[0-9]+$"));
        conflictReportMapper.delete(wrapper);
    }

    private Context buildContext(List<Schedule> schedules, Long semesterId) {
        Context context = new Context();
        context.schedules = schedules;

        List<Long> teacherIds = schedules.stream().map(Schedule::getTeacherId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> classIds = schedules.stream().map(Schedule::getClassId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> classroomIds = schedules.stream().map(Schedule::getClassroomId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> courseIds = schedules.stream().map(Schedule::getCourseId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> timeSlotIds = schedules.stream().map(Schedule::getTimeSlotId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        List<TeachingTask> allTasks = teachingTaskMapper.selectList(new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getStatus, 1)
                .eq(TeachingTask::getSemesterId, semesterId));
        context.taskMap = allTasks.stream().collect(Collectors.toMap(TeachingTask::getId, Function.identity(), (a, b) -> a));
        teacherIds = new ArrayList<>(teacherIds);
        classIds = new ArrayList<>(classIds);
        courseIds = new ArrayList<>(courseIds);
        for (TeachingTask task : allTasks) {
            if (task.getTeacherId() != null && !teacherIds.contains(task.getTeacherId())) {
                teacherIds.add(task.getTeacherId());
            }
            if (task.getClassId() != null && !classIds.contains(task.getClassId())) {
                classIds.add(task.getClassId());
            }
            if (task.getCourseId() != null && !courseIds.contains(task.getCourseId())) {
                courseIds.add(task.getCourseId());
            }
        }
        context.teacherMap = teacherMapper.selectList(new LambdaQueryWrapper<Teacher>().in(!teacherIds.isEmpty(), Teacher::getId, teacherIds))
                .stream().collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        context.classMap = classInfoMapper.selectList(new LambdaQueryWrapper<ClassInfo>().in(!classIds.isEmpty(), ClassInfo::getId, classIds))
                .stream().collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));
        context.classroomMap = classroomMapper.selectList(new LambdaQueryWrapper<Classroom>().in(!classroomIds.isEmpty(), Classroom::getId, classroomIds))
                .stream().collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));
        context.courseMap = courseMapper.selectList(new LambdaQueryWrapper<Course>().in(!courseIds.isEmpty(), Course::getId, courseIds))
                .stream().collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        context.timeSlotMap = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>().in(!timeSlotIds.isEmpty(), TimeSlot::getId, timeSlotIds))
                .stream().collect(Collectors.toMap(TimeSlot::getId, Function.identity(), (a, b) -> a));

        context.unavailableMap = unavailableTimeMapper.selectList(new LambdaQueryWrapper<TeacherUnavailableTime>()
                        .eq(TeacherUnavailableTime::getStatus, 1))
                .stream()
                .collect(Collectors.toMap(
                        item -> buildPairKey(item.getTeacherId(), item.getTimeSlotId()),
                        Function.identity(),
                        (a, b) -> a
                ));

        context.teacherDailyLimit = ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS");
        context.classDailyLimit = ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS");
        return context;
    }

    private void detectTeacherConflicts(String baseReportNo, Context context, List<ScheduleConflictReport> reports) {
        Map<String, List<Schedule>> grouped = context.schedules.stream()
                .filter(s -> s.getTeacherId() != null && s.getTimeSlotId() != null)
                .collect(Collectors.groupingBy(s -> buildPairKey(s.getTeacherId(), s.getTimeSlotId())));
        for (List<Schedule> group : grouped.values()) {
            if (group.size() <= 1) {
                continue;
            }
            Schedule sample = group.get(0);
            Teacher teacher = context.teacherMap.get(sample.getTeacherId());
            TimeSlot slot = context.timeSlotMap.get(sample.getTimeSlotId());
            String teacherName = teacher != null ? teacher.getName() : "未知教师";
            String timeLabel = formatTimeLabel(slot);
            String courseSummary = summarizeSchedules(group, context);
            reports.add(buildReport(
                    baseReportNo,
                    reports.size() + 1,
                    "TEACHER_CONFLICT",
                    "TEACHER",
                    sample.getTeacherId(),
                    teacherName,
                    sample.getTimeSlotId(),
                    joinScheduleIds(group),
                    teacherName + "在" + timeLabel + "存在" + group.size() + "条课程安排：" + courseSummary,
                    "建议调整其中一门课程到其他时间段，避免教师同一时间承担多门课程"
            ));
        }
    }

    private void detectClassConflicts(String baseReportNo, Context context, List<ScheduleConflictReport> reports) {
        Map<String, List<Schedule>> grouped = context.schedules.stream()
                .filter(s -> s.getClassId() != null && s.getTimeSlotId() != null)
                .collect(Collectors.groupingBy(s -> buildPairKey(s.getClassId(), s.getTimeSlotId())));
        for (List<Schedule> group : grouped.values()) {
            if (group.size() <= 1) {
                continue;
            }
            Schedule sample = group.get(0);
            ClassInfo classInfo = context.classMap.get(sample.getClassId());
            TimeSlot slot = context.timeSlotMap.get(sample.getTimeSlotId());
            String className = classInfo != null ? classInfo.getClassName() : "未知班级";
            String timeLabel = formatTimeLabel(slot);
            String courseSummary = summarizeSchedules(group, context);
            reports.add(buildReport(
                    baseReportNo,
                    reports.size() + 1,
                    "CLASS_CONFLICT",
                    "CLASS",
                    sample.getClassId(),
                    className,
                    sample.getTimeSlotId(),
                    joinScheduleIds(group),
                    className + "在" + timeLabel + "被安排了" + group.size() + "门课程：" + courseSummary,
                    "建议保留最合适的一门课程，其他课程调整到该班级的空闲时间段"
            ));
        }
    }

    private void detectClassroomConflicts(String baseReportNo, Context context, List<ScheduleConflictReport> reports) {
        Map<String, List<Schedule>> grouped = context.schedules.stream()
                .filter(s -> s.getClassroomId() != null && s.getTimeSlotId() != null)
                .collect(Collectors.groupingBy(s -> buildPairKey(s.getClassroomId(), s.getTimeSlotId())));
        for (List<Schedule> group : grouped.values()) {
            if (group.size() <= 1) {
                continue;
            }
            Schedule sample = group.get(0);
            Classroom classroom = context.classroomMap.get(sample.getClassroomId());
            TimeSlot slot = context.timeSlotMap.get(sample.getTimeSlotId());
            String roomName = classroom != null ? classroom.getRoomName() : "未知教室";
            String timeLabel = formatTimeLabel(slot);
            String courseSummary = summarizeSchedules(group, context);
            reports.add(buildReport(
                    baseReportNo,
                    reports.size() + 1,
                    "ROOM_CONFLICT",
                    "CLASSROOM",
                    sample.getClassroomId(),
                    roomName,
                    sample.getTimeSlotId(),
                    joinScheduleIds(group),
                    roomName + "在" + timeLabel + "被重复占用，共涉及" + group.size() + "条排课：" + courseSummary,
                    "建议为其中部分课程更换空闲教室，避免同一教室同一时间重复使用"
            ));
        }
    }

    private void detectCapacityIssues(String baseReportNo, Context context, List<ScheduleConflictReport> reports) {
        for (Schedule schedule : context.schedules) {
            ClassInfo classInfo = context.classMap.get(schedule.getClassId());
            Classroom classroom = context.classroomMap.get(schedule.getClassroomId());
            if (classInfo == null || classroom == null || classInfo.getStudentCount() == null || classroom.getCapacity() == null) {
                continue;
            }
            if (classInfo.getStudentCount() <= classroom.getCapacity()) {
                continue;
            }
            reports.add(buildReport(
                    baseReportNo,
                    reports.size() + 1,
                    "CLASSROOM_CAPACITY_NOT_ENOUGH",
                    "CLASSROOM",
                    classroom.getId(),
                    classroom.getRoomName(),
                    schedule.getTimeSlotId(),
                    String.valueOf(schedule.getId()),
                    classInfo.getClassName() + "人数为" + classInfo.getStudentCount() + "，当前安排在" + classroom.getRoomName()
                            + "，教室容量仅为" + classroom.getCapacity(),
                    "建议更换容量更大的教室，或拆分上课班级后再重新安排"
            ));
        }
    }

    private void detectRoomTypeMismatch(String baseReportNo, Context context, List<ScheduleConflictReport> reports) {
        for (Schedule schedule : context.schedules) {
            Course course = context.courseMap.get(schedule.getCourseId());
            Classroom classroom = context.classroomMap.get(schedule.getClassroomId());
            if (course == null || classroom == null) {
                continue;
            }
            String mismatchReason = getRoomTypeMismatchReason(course, classroom);
            if (mismatchReason == null) {
                continue;
            }
            reports.add(buildReport(
                    baseReportNo,
                    reports.size() + 1,
                    "ROOM_TYPE_MISMATCH",
                    "CLASSROOM",
                    classroom.getId(),
                    classroom.getRoomName(),
                    schedule.getTimeSlotId(),
                    String.valueOf(schedule.getId()),
                    mismatchReason,
                    buildRoomTypeSuggestion(course)
            ));
        }
    }

    private void detectTeacherUnavailableIssues(String baseReportNo, Context context, List<ScheduleConflictReport> reports) {
        for (Schedule schedule : context.schedules) {
            TeacherUnavailableTime unavailableTime = context.unavailableMap.get(buildPairKey(schedule.getTeacherId(), schedule.getTimeSlotId()));
            if (unavailableTime == null) {
                continue;
            }
            Teacher teacher = context.teacherMap.get(schedule.getTeacherId());
            TimeSlot slot = context.timeSlotMap.get(schedule.getTimeSlotId());
            String teacherName = teacher != null ? teacher.getName() : "未知教师";
            String timeLabel = formatTimeLabel(slot);
            String reason = unavailableTime.getReason() != null && !unavailableTime.getReason().isBlank()
                    ? unavailableTime.getReason()
                    : "已设置禁排时间";
            reports.add(buildReport(
                    baseReportNo,
                    reports.size() + 1,
                    "TEACHER_UNAVAILABLE",
                    "TEACHER",
                    schedule.getTeacherId(),
                    teacherName,
                    schedule.getTimeSlotId(),
                    String.valueOf(schedule.getId()),
                    teacherName + "在" + timeLabel + "存在排课，但该时间段已设置禁排，原因：" + reason,
                    "建议调整课程到教师可排时间，或在确认无误后重新评估该教师禁排设置"
            ));
        }
    }

    private void detectUnfinishedTasks(String baseReportNo, Context context, List<ScheduleConflictReport> reports) {
        Map<Long, List<Schedule>> taskScheduleMap = context.schedules.stream()
                .filter(s -> s.getTeachingTaskId() != null)
                .collect(Collectors.groupingBy(Schedule::getTeachingTaskId));

        for (TeachingTask task : context.taskMap.values()) {
            int requiredSlots = calculateRequiredSlots(task);
            List<Schedule> taskSchedules = taskScheduleMap.getOrDefault(task.getId(), Collections.emptyList());
            int scheduledSlots = taskSchedules.size();
            if (scheduledSlots >= requiredSlots) {
                continue;
            }
            Teacher teacher = context.teacherMap.get(task.getTeacherId());
            ClassInfo classInfo = context.classMap.get(task.getClassId());
            Course course = context.courseMap.get(task.getCourseId());
            String taskName = buildTaskName(course, teacher, classInfo);
            reports.add(buildReport(
                    baseReportNo,
                    reports.size() + 1,
                    "TASK_NOT_FULLY_SCHEDULED",
                    "TASK",
                    task.getId(),
                    taskName,
                    null,
                    joinScheduleIds(taskSchedules),
                    taskName + "每周需要安排" + requiredSlots + "个大节，当前只排了" + scheduledSlots + "个大节，仍缺少"
                            + (requiredSlots - scheduledSlots) + "个大节",
                    "建议优先补排该教学任务，必要时调整教师禁排、每日上限或教室资源配置"
            ));
        }
    }

    private void detectTeacherDailyOverload(String baseReportNo, Context context, List<ScheduleConflictReport> reports) {
        if (context.teacherDailyLimit <= 0) {
            return;
        }
        Map<String, List<Schedule>> grouped = context.schedules.stream()
                .filter(s -> s.getTeacherId() != null)
                .collect(Collectors.groupingBy(s -> buildDailyKey(s.getTeacherId(), getDayOfWeek(context.timeSlotMap.get(s.getTimeSlotId())))));
        for (List<Schedule> group : grouped.values()) {
            if (group.size() <= context.teacherDailyLimit) {
                continue;
            }
            Schedule sample = group.get(0);
            Teacher teacher = context.teacherMap.get(sample.getTeacherId());
            Integer dayOfWeek = getDayOfWeek(context.timeSlotMap.get(sample.getTimeSlotId()));
            String teacherName = teacher != null ? teacher.getName() : "未知教师";
            reports.add(buildReport(
                    baseReportNo,
                    reports.size() + 1,
                    "TEACHER_DAILY_LIMIT",
                    "TEACHER",
                    sample.getTeacherId(),
                    teacherName,
                    null,
                    joinScheduleIds(group),
                    teacherName + "在" + weekdayText(dayOfWeek) + "共安排了" + group.size() + "个大节，超过规则上限"
                            + context.teacherDailyLimit + "个大节",
                    "建议将其中部分课程调整到其他工作日，减轻教师单日授课压力"
            ));
        }
    }

    private void detectClassDailyOverload(String baseReportNo, Context context, List<ScheduleConflictReport> reports) {
        if (context.classDailyLimit <= 0) {
            return;
        }
        Map<String, List<Schedule>> grouped = context.schedules.stream()
                .filter(s -> s.getClassId() != null)
                .collect(Collectors.groupingBy(s -> buildDailyKey(s.getClassId(), getDayOfWeek(context.timeSlotMap.get(s.getTimeSlotId())))));
        for (List<Schedule> group : grouped.values()) {
            if (group.size() <= context.classDailyLimit) {
                continue;
            }
            Schedule sample = group.get(0);
            ClassInfo classInfo = context.classMap.get(sample.getClassId());
            Integer dayOfWeek = getDayOfWeek(context.timeSlotMap.get(sample.getTimeSlotId()));
            String className = classInfo != null ? classInfo.getClassName() : "未知班级";
            reports.add(buildReport(
                    baseReportNo,
                    reports.size() + 1,
                    "CLASS_DAILY_LIMIT",
                    "CLASS",
                    sample.getClassId(),
                    className,
                    null,
                    joinScheduleIds(group),
                    className + "在" + weekdayText(dayOfWeek) + "共安排了" + group.size() + "个大节，超过规则上限"
                            + context.classDailyLimit + "个大节",
                    "建议将部分课程调整到其他日期，避免班级当天课程过于集中"
            ));
        }
    }

    private void fillRelationFields(List<ScheduleConflictReportVo> records) {
        if (records.isEmpty()) {
            return;
        }

        List<Long> timeSlotIds = records.stream()
                .map(ScheduleConflictReportVo::getTimeSlotId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, TimeSlot> timeSlotMap = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
                        .in(!timeSlotIds.isEmpty(), TimeSlot::getId, timeSlotIds))
                .stream()
                .collect(Collectors.toMap(TimeSlot::getId, Function.identity(), (a, b) -> a));

        Set<Long> relatedScheduleIds = new LinkedHashSet<>();
        for (ScheduleConflictReportVo record : records) {
            parseScheduleIds(record.getRelatedScheduleIds()).forEach(relatedScheduleIds::add);
        }
        Map<Long, Schedule> scheduleMap = relatedScheduleIds.isEmpty()
                ? Collections.emptyMap()
                : scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>().in(Schedule::getId, relatedScheduleIds))
                .stream().collect(Collectors.toMap(Schedule::getId, Function.identity(), (a, b) -> a));

        for (ScheduleConflictReportVo record : records) {
            record.setReportNo(normalizeReportNo(record.getReportNo()));
            if (record.getTimeSlotId() != null) {
                TimeSlot slot = timeSlotMap.get(record.getTimeSlotId());
                record.setTimeSlotName(formatTimeLabel(slot));
            } else if ("TASK_NOT_FULLY_SCHEDULED".equals(record.getConflictType())) {
                record.setTimeSlotName("全周");
            } else {
                Integer dayOfWeek = extractDayOfWeekFromRelatedSchedules(record, scheduleMap, timeSlotMap);
                record.setTimeSlotName(dayOfWeek != null ? weekdayText(dayOfWeek) : "-");
            }
        }
    }

    private ScheduleConflictReportVo toVo(ScheduleConflictReport entity) {
        ScheduleConflictReportVo vo = new ScheduleConflictReportVo();
        vo.setId(entity.getId());
        vo.setSemesterId(entity.getSemesterId());
        vo.setReportNo(entity.getReportNo());
        vo.setConflictType(entity.getConflictType());
        vo.setObjectType(entity.getObjectType());
        vo.setObjectId(entity.getObjectId());
        vo.setObjectName(entity.getObjectName());
        vo.setTimeSlotId(entity.getTimeSlotId());
        vo.setRelatedScheduleIds(entity.getRelatedScheduleIds());
        vo.setDescription(entity.getDescription());
        vo.setSuggestion(entity.getSuggestion());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private ScheduleConflictReport buildReport(
            String baseReportNo,
            int sequence,
            String conflictType,
            String objectType,
            Long objectId,
            String objectName,
            Long timeSlotId,
            String relatedScheduleIds,
            String description,
            String suggestion
    ) {
        ScheduleConflictReport report = new ScheduleConflictReport();
        report.setReportNo(baseReportNo + "-" + String.format("%02d", sequence));
        report.setConflictType(conflictType);
        report.setObjectType(objectType);
        report.setObjectId(objectId);
        report.setObjectName(objectName);
        report.setTimeSlotId(timeSlotId);
        report.setRelatedScheduleIds(relatedScheduleIds);
        report.setDescription(description);
        report.setSuggestion(suggestion);
        report.setCreateTime(LocalDateTime.now());
        return report;
    }

    private String summarizeSchedules(List<Schedule> schedules, Context context) {
        return schedules.stream()
                .map(schedule -> {
                    Course course = context.courseMap.get(schedule.getCourseId());
                    ClassInfo classInfo = context.classMap.get(schedule.getClassId());
                    Classroom classroom = context.classroomMap.get(schedule.getClassroomId());
                    String courseName = course != null ? course.getCourseName() : "未知课程";
                    String className = classInfo != null ? classInfo.getClassName() : "未知班级";
                    String roomName = classroom != null ? classroom.getRoomName() : "未知教室";
                    return courseName + "（" + className + "，" + roomName + "）";
                })
                .distinct()
                .collect(Collectors.joining("；"));
    }

    private String joinScheduleIds(List<Schedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return "";
        }
        return schedules.stream()
                .map(Schedule::getId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private int calculateRequiredSlots(TeachingTask task) {
        if (task == null || task.getWeeklyHours() == null) {
            return 0;
        }
        return (int) Math.ceil(task.getWeeklyHours() / 2.0);
    }

    private String getRoomTypeMismatchReason(Course course, Classroom classroom) {
        if (course == null || classroom == null) {
            return null;
        }
        if (CourseType.EXPERIMENT.getCode().equals(course.getCourseType()) && !"LAB".equals(classroom.getRoomType())) {
            return course.getCourseName() + "为实验课，但当前安排在" + classroom.getRoomName() + "，该教室类型为" + roomTypeText(classroom.getRoomType());
        }
        if (CourseType.COMPUTER.getCode().equals(course.getCourseType()) && !RoomType.COMPUTER.getCode().equals(classroom.getRoomType())) {
            return course.getCourseName() + "为机房课，但当前安排在" + classroom.getRoomName() + "，该教室类型为" + roomTypeText(classroom.getRoomType());
        }
        return null;
    }

    private String buildRoomTypeSuggestion(Course course) {
        if (course == null) {
            return "建议将课程调整到与课程类型匹配的教室";
        }
        if (CourseType.EXPERIMENT.getCode().equals(course.getCourseType())) {
            return "建议将实验课调整到实验室，并确认实验室设备满足课程需求";
        }
        if (CourseType.COMPUTER.getCode().equals(course.getCourseType())) {
            return "建议将机房课调整到机房，并确认机位数量满足班级人数";
        }
        return "建议将课程调整到与课程类型匹配的教室";
    }

    private String roomTypeText(String roomType) {
        if (RoomType.LAB.getCode().equals(roomType)) {
            return "实验室";
        }
        if (RoomType.COMPUTER.getCode().equals(roomType)) {
            return "机房";
        }
        if ("NORMAL".equals(roomType)) {
            return "普通教室";
        }
        if ("SPORT".equals(roomType)) {
            return "体育场地";
        }
        return roomType == null || roomType.isBlank() ? "未知类型" : roomType;
    }

    private String buildTaskName(Course course, Teacher teacher, ClassInfo classInfo) {
        String courseName = course != null ? course.getCourseName() : "未知课程";
        String teacherName = teacher != null ? teacher.getName() : "未知教师";
        String className = classInfo != null ? classInfo.getClassName() : "未知班级";
        return courseName + " / " + teacherName + " / " + className;
    }

    private String buildPairKey(Long left, Long right) {
        return String.valueOf(left) + "_" + String.valueOf(right);
    }

    private String buildDailyKey(Long objectId, Integer dayOfWeek) {
        return String.valueOf(objectId) + "_" + String.valueOf(dayOfWeek);
    }

    private String normalizeReportNo(String reportNo) {
        int suffixIndex = reportNo.lastIndexOf('-');
        if (suffixIndex > 0) {
            String suffix = reportNo.substring(suffixIndex + 1);
            boolean numericSuffix = suffix.chars().allMatch(Character::isDigit);
            if (numericSuffix) {
                return reportNo.substring(0, suffixIndex);
            }
        }
        return reportNo;
    }

    private String escapeRegex(String value) {
        return value.replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace("|", "\\|")
                .replace("?", "\\?")
                .replace("*", "\\*")
                .replace("+", "\\+")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    private List<Long> parseScheduleIds(String relatedScheduleIds) {
        if (relatedScheduleIds == null || relatedScheduleIds.isBlank()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : relatedScheduleIds.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // ignore invalid id
            }
        }
        return ids;
    }

    private Integer extractDayOfWeekFromRelatedSchedules(
            ScheduleConflictReportVo record,
            Map<Long, Schedule> scheduleMap,
            Map<Long, TimeSlot> timeSlotMap
    ) {
        for (Long scheduleId : parseScheduleIds(record.getRelatedScheduleIds())) {
            Schedule schedule = scheduleMap.get(scheduleId);
            if (schedule == null) {
                continue;
            }
            TimeSlot slot = timeSlotMap.get(schedule.getTimeSlotId());
            if (slot != null && slot.getDayOfWeek() != null) {
                return slot.getDayOfWeek();
            }
        }
        return null;
    }

    private Integer getDayOfWeek(TimeSlot slot) {
        return slot != null ? slot.getDayOfWeek() : null;
    }

    private String formatTimeLabel(TimeSlot slot) {
        return slot != null ? slot.getTimeLabel() : "-";
    }

    private String weekdayText(Integer dayOfWeek) {
        return WEEKDAY_TEXT.getOrDefault(dayOfWeek, "未知日期");
    }

    private Long resolveSemesterId(Long semesterId) {
        if (semesterId != null) {
            return semesterId;
        }
        try {
            return semesterService.getCurrentSemester().getId();
        } catch (BusinessException e) {
            throw new BusinessException("未找到当前学期，无法生成冲突报告");
        }
    }

    private static class Context {
        private List<Schedule> schedules;
        private Map<Long, TeachingTask> taskMap;
        private Map<Long, Teacher> teacherMap;
        private Map<Long, ClassInfo> classMap;
        private Map<Long, Classroom> classroomMap;
        private Map<Long, Course> courseMap;
        private Map<Long, TimeSlot> timeSlotMap;
        private Map<String, TeacherUnavailableTime> unavailableMap;
        private int teacherDailyLimit;
        private int classDailyLimit;
    }

    @Data
    public static class GenerateResult {
        private final String reportNo;
        private final int conflictCount;
        private final String message;
    }
}
