package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleScoreReportService {

    /**
     * 根据当前排课结果生成质量评分报告。
     * 这里的评分不是“越多越好”的统计，而是从满分开始按冲突和排课体验逐项扣分。
     */
    private static final int FULL_SCORE = 100;

    private static final int DEDUCT_PER_CONFLICT = 10;
    private static final int DEDUCT_PER_UNFINISHED_TASK = 4;
    private static final int DEDUCT_PER_TEACHER_OVERLOAD = 3;
    private static final int DEDUCT_PER_CLASS_OVERLOAD = 2;
    private static final int DEDUCT_PER_FRIDAY_AFTERNOON = 1;

    private final ScheduleScoreReportMapper scoreReportMapper;
    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TeacherUnavailableTimeMapper unavailableTimeMapper;
    private final ScheduleRuleService ruleService;
    private final SemesterService semesterService;

    public ScheduleScoreReport getLatest() {
        return getLatest(null);
    }

    public ScheduleScoreReport getLatest(Long semesterId) {
        Long effectiveSemesterId = resolveSemesterId(semesterId);
        return scoreReportMapper.selectOne(new LambdaQueryWrapper<ScheduleScoreReport>()
                .eq(ScheduleScoreReport::getSemesterId, effectiveSemesterId)
                .orderByDesc(ScheduleScoreReport::getCreateTime)
                .last("LIMIT 1"));
    }

    public Page<ScheduleScoreReport> list(String grade, LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        return list(null, grade, startTime, endTime, page, size);
    }

    public Page<ScheduleScoreReport> list(Long semesterId, String grade, LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        Long effectiveSemesterId = resolveSemesterId(semesterId);
        LambdaQueryWrapper<ScheduleScoreReport> wrapper = new LambdaQueryWrapper<ScheduleScoreReport>()
                .eq(ScheduleScoreReport::getSemesterId, effectiveSemesterId)
                .orderByDesc(ScheduleScoreReport::getCreateTime);
        if (grade != null && !grade.isBlank()) {
            wrapper.eq(ScheduleScoreReport::getGrade, grade.trim());
        }
        if (startTime != null) {
            wrapper.ge(ScheduleScoreReport::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(ScheduleScoreReport::getCreateTime, endTime);
        }
        return scoreReportMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 生成一次完整评分，并把结果持久化，供列表和详情页直接查询。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScoreResult generate() {
        return generate(null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScoreResult generate(Long semesterId) {
        Long effectiveSemesterId = resolveSemesterId(semesterId);
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDeleted, 0)
                .eq(Schedule::getSemesterId, effectiveSemesterId));

        Context context = buildContext(effectiveSemesterId, schedules);

        int hardConflictCount = countHardConflicts(context);
        int unfinishedTaskCount = countUnfinishedTasks(context);
        int teacherOverloadCount = countTeacherOverload(context);
        int classOverloadCount = countClassOverload(context);
        int fridayAfternoonCount = countFridayAfternoon(context);

        int totalDeduction = hardConflictCount * DEDUCT_PER_CONFLICT
                + unfinishedTaskCount * DEDUCT_PER_UNFINISHED_TASK
                + teacherOverloadCount * DEDUCT_PER_TEACHER_OVERLOAD
                + classOverloadCount * DEDUCT_PER_CLASS_OVERLOAD
                + fridayAfternoonCount * DEDUCT_PER_FRIDAY_AFTERNOON;

        int score = Math.max(0, FULL_SCORE - totalDeduction);
        String grade = calculateGrade(score);

        List<String> deductionDetail = buildDeductionDetail(
                hardConflictCount, unfinishedTaskCount, teacherOverloadCount,
                classOverloadCount, fridayAfternoonCount);
        List<String> suggestions = buildSuggestions(
                hardConflictCount, unfinishedTaskCount, teacherOverloadCount,
                classOverloadCount, fridayAfternoonCount);

        ScheduleScoreReport report = new ScheduleScoreReport();
        report.setSemesterId(effectiveSemesterId);
        report.setScore(score);
        report.setGrade(grade);
        report.setGradeName(gradeNameText(grade));
        report.setConflictCount(hardConflictCount);
        report.setUnfinishedTaskCount(unfinishedTaskCount);
        report.setTeacherOverloadCount(teacherOverloadCount);
        report.setClassOverloadCount(classOverloadCount);
        report.setFridayAfternoonCount(fridayAfternoonCount);
        report.setDeductionDetail(deductionDetail.stream().collect(Collectors.joining("\n")));
        report.setSuggestion(suggestions.stream().collect(Collectors.joining("\n")));
        report.setCreateTime(LocalDateTime.now());
        scoreReportMapper.insert(report);

        ScoreResult result = new ScoreResult();
        result.setScore(score);
        result.setGrade(grade);
        result.setGradeName(gradeNameText(grade));
        result.setConflictCount(hardConflictCount);
        result.setUnfinishedTaskCount(unfinishedTaskCount);
        result.setTeacherOverloadCount(teacherOverloadCount);
        result.setClassOverloadCount(classOverloadCount);
        result.setFridayAfternoonCount(fridayAfternoonCount);
        result.setDeductionDetail(deductionDetail);
        result.setSuggestion(suggestions);
        return result;
    }

    /**
     * 把评分过程中会重复使用的基础数据一次性查出，避免后续各项统计重复访问数据库。
     * 同时把“已排课数据”和“全部启用中的教学任务”放到同一上下文里，便于判断未排满任务。
     */
    private Context buildContext(Long semesterId, List<Schedule> schedules) {
        Context context = new Context();
        context.schedules = schedules;

        List<Long> classIds = schedules.stream().map(Schedule::getClassId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> classroomIds = schedules.stream().map(Schedule::getClassroomId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> courseIds = schedules.stream().map(Schedule::getCourseId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> timeSlotIds = schedules.stream().map(Schedule::getTimeSlotId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        List<TeachingTask> allTasks = teachingTaskMapper.selectList(new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getDeleted, 0)
                .eq(TeachingTask::getStatus, 1)
                .eq(TeachingTask::getSemesterId, semesterId));
        context.taskMap = allTasks.stream().collect(Collectors.toMap(TeachingTask::getId, Function.identity(), (a, b) -> a));

        classIds = new ArrayList<>(classIds);
        courseIds = new ArrayList<>(courseIds);
        for (TeachingTask task : allTasks) {
            if (task.getClassId() != null && !classIds.contains(task.getClassId())) classIds.add(task.getClassId());
            if (task.getCourseId() != null && !courseIds.contains(task.getCourseId())) courseIds.add(task.getCourseId());
        }

        context.classMap = classInfoMapper.selectList(new LambdaQueryWrapper<ClassInfo>().in(!classIds.isEmpty(), ClassInfo::getId, classIds))
                .stream().collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));
        context.classroomMap = classroomMapper.selectList(new LambdaQueryWrapper<Classroom>().in(!classroomIds.isEmpty(), Classroom::getId, classroomIds))
                .stream().collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));
        context.courseMap = courseMapper.selectList(new LambdaQueryWrapper<Course>().in(!courseIds.isEmpty(), Course::getId, courseIds))
                .stream().collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        context.timeSlotMap = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>().in(!timeSlotIds.isEmpty(), TimeSlot::getId, timeSlotIds))
                .stream().collect(Collectors.toMap(TimeSlot::getId, Function.identity(), (a, b) -> a));

        context.unavailableMap = unavailableTimeMapper.selectList(new LambdaQueryWrapper<TeacherUnavailableTime>()
                        .eq(TeacherUnavailableTime::getDeleted, 0)
                        .eq(TeacherUnavailableTime::getStatus, 1))
                .stream()
                .collect(Collectors.toMap(item -> buildPairKey(item.getTeacherId(), item.getTimeSlotId()), Function.identity(), (a, b) -> a));

        context.teacherDailyLimit = ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS");
        context.classDailyLimit = ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS");
        return context;
    }

    private Long resolveSemesterId(Long semesterId) {
        if (semesterId != null) {
            semesterService.getById(semesterId);
            return semesterId;
        }
        return semesterService.getCurrentSemester().getId();
    }

    /**
     * 硬冲突按“冲突组”计数，不按具体排课记录条数计数。
     * 例如同一教师同一时间排了 3 条课，只记 1 个教师时间冲突组。
     */
    private int countHardConflicts(Context context) {
        int count = 0;

        // 教师、班级、教室占用冲突都按“同一对象 + 同一时间段”分组统计。
        Map<String, List<Schedule>> byTeacher = context.schedules.stream()
                .filter(s -> s.getTeacherId() != null && s.getTimeSlotId() != null)
                .collect(Collectors.groupingBy(s -> buildPairKey(s.getTeacherId(), s.getTimeSlotId())));
        for (List<Schedule> group : byTeacher.values()) {
            if (group.size() > 1) count++;
        }

        Map<String, List<Schedule>> byClass = context.schedules.stream()
                .filter(s -> s.getClassId() != null && s.getTimeSlotId() != null)
                .collect(Collectors.groupingBy(s -> buildPairKey(s.getClassId(), s.getTimeSlotId())));
        for (List<Schedule> group : byClass.values()) {
            if (group.size() > 1) count++;
        }

        Map<String, List<Schedule>> byRoom = context.schedules.stream()
                .filter(s -> s.getClassroomId() != null && s.getTimeSlotId() != null)
                .collect(Collectors.groupingBy(s -> buildPairKey(s.getClassroomId(), s.getTimeSlotId())));
        for (List<Schedule> group : byRoom.values()) {
            if (group.size() > 1) count++;
        }

        // 容量不足和教室类型不匹配按单条排课记录计数，因为每条记录都代表一次明确的不合理安排。
        for (Schedule schedule : context.schedules) {
            ClassInfo classInfo = context.classMap.get(schedule.getClassId());
            Classroom classroom = context.classroomMap.get(schedule.getClassroomId());
            if (classInfo != null && classroom != null && classInfo.getStudentCount() != null && classroom.getCapacity() != null) {
                if (classInfo.getStudentCount() > classroom.getCapacity()) count++;
            }
        }

        for (Schedule schedule : context.schedules) {
            Course course = context.courseMap.get(schedule.getCourseId());
            Classroom classroom = context.classroomMap.get(schedule.getClassroomId());
            if (course != null && classroom != null) {
                if (isRoomTypeMismatch(course, classroom)) count++;
            }
        }

        // 教师禁排时间一旦被占用，也按单条记录视为一次硬冲突。
        for (Schedule schedule : context.schedules) {
            TeacherUnavailableTime ut = context.unavailableMap.get(buildPairKey(schedule.getTeacherId(), schedule.getTimeSlotId()));
            if (ut != null) count++;
        }

        return count;
    }

    /**
     * 未排满统计关注的是“教学任务是否达到周课时要求”，不是单看是否存在排课记录。
     */
    private int countUnfinishedTasks(Context context) {
        Map<Long, List<Schedule>> taskScheduleMap = context.schedules.stream()
                .filter(s -> s.getTeachingTaskId() != null)
                .collect(Collectors.groupingBy(Schedule::getTeachingTaskId));

        int count = 0;
        for (TeachingTask task : context.taskMap.values()) {
            int requiredSlots = calculateRequiredSlots(task);
            int scheduledSlots = taskScheduleMap.getOrDefault(task.getId(), Collections.emptyList()).size();
            if (scheduledSlots < requiredSlots) count++;
        }
        return count;
    }

    /**
     * 当天课时上限来自规则表。
     * 这里只统计超限的“教师-日期”组合数量，用来反映课表集中度问题。
     */
    private int countTeacherOverload(Context context) {
        if (context.teacherDailyLimit <= 0) return 0;
        Map<String, List<Schedule>> grouped = context.schedules.stream()
                .filter(s -> s.getTeacherId() != null)
                .collect(Collectors.groupingBy(s -> buildDailyKey(s.getTeacherId(), getDayOfWeek(context.timeSlotMap.get(s.getTimeSlotId())))));
        int count = 0;
        for (List<Schedule> group : grouped.values()) {
            if (group.size() > context.teacherDailyLimit) count++;
        }
        return count;
    }

    /**
     * 和教师过载同口径，统计超限的“班级-日期”组合数量。
     */
    private int countClassOverload(Context context) {
        if (context.classDailyLimit <= 0) return 0;
        Map<String, List<Schedule>> grouped = context.schedules.stream()
                .filter(s -> s.getClassId() != null)
                .collect(Collectors.groupingBy(s -> buildDailyKey(s.getClassId(), getDayOfWeek(context.timeSlotMap.get(s.getTimeSlotId())))));
        int count = 0;
        for (List<Schedule> group : grouped.values()) {
            if (group.size() > context.classDailyLimit) count++;
        }
        return count;
    }

    /**
     * 周五下午不是硬冲突，但通常被视为体验较差的排课位置，因此只做轻度扣分。
     */
    private int countFridayAfternoon(Context context) {
        int count = 0;
        for (Schedule schedule : context.schedules) {
            TimeSlot slot = context.timeSlotMap.get(schedule.getTimeSlotId());
            if (slot != null && slot.getDayOfWeek() != null && slot.getDayOfWeek() == 5
                    && slot.getPeriodNo() != null && slot.getPeriodNo() >= 3) {
                count++;
            }
        }
        return count;
    }

    /**
     * 成绩分级只依赖最终分数，阈值在这里集中维护，便于后续调整评分标准。
     */
    private String calculateGrade(int score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 80) return "GOOD";
        if (score >= 70) return "AVERAGE";
        if (score >= 60) return "POOR";
        return "BAD";
    }

    private String gradeNameText(String grade) {
        return switch (grade) {
            case "EXCELLENT" -> "优秀";
            case "GOOD" -> "良好";
            case "AVERAGE" -> "一般";
            case "POOR" -> "较差";
            case "BAD" -> "需要调整";
            default -> grade;
        };
    }

    private List<String> buildDeductionDetail(int hardConflictCount, int unfinishedTaskCount,
                                               int teacherOverloadCount, int classOverloadCount,
                                               int fridayAfternoonCount) {
        List<String> details = new ArrayList<>();
        if (hardConflictCount > 0) {
            int deduction = hardConflictCount * DEDUCT_PER_CONFLICT;
            details.add(hardConflictCount + " 个硬冲突（教师/班级/教室冲突、容量不足、类型不匹配、禁排冲突）：-" + deduction + " 分");
        }
        if (unfinishedTaskCount > 0) {
            int deduction = unfinishedTaskCount * DEDUCT_PER_UNFINISHED_TASK;
            details.add(unfinishedTaskCount + " 个教学任务未排满：-" + deduction + " 分");
        }
        if (teacherOverloadCount > 0) {
            int deduction = teacherOverloadCount * DEDUCT_PER_TEACHER_OVERLOAD;
            details.add(teacherOverloadCount + " 名教师当天课程过多：-" + deduction + " 分");
        }
        if (classOverloadCount > 0) {
            int deduction = classOverloadCount * DEDUCT_PER_CLASS_OVERLOAD;
            details.add(classOverloadCount + " 个班级当天课程过多：-" + deduction + " 分");
        }
        if (fridayAfternoonCount > 0) {
            int deduction = fridayAfternoonCount * DEDUCT_PER_FRIDAY_AFTERNOON;
            details.add("周五下午课程较多（共 " + fridayAfternoonCount + " 个大节）：-" + deduction + " 分");
        }
        if (details.isEmpty()) {
            details.add("无扣分项");
        }
        return details;
    }

    private List<String> buildSuggestions(int hardConflictCount, int unfinishedTaskCount,
                                           int teacherOverloadCount, int classOverloadCount,
                                           int fridayAfternoonCount) {
        List<String> suggestions = new ArrayList<>();
        if (hardConflictCount > 0) {
            suggestions.add("建议优先处理硬冲突：调整冲突时间段、更换教室或重新分配教师");
        }
        if (unfinishedTaskCount > 0) {
            suggestions.add("建议优先补排未排满的教学任务，必要时调整教师禁排时间或教室资源配置");
        }
        if (teacherOverloadCount > 0) {
            suggestions.add("建议调整课程过于集中的教师课表，将部分课程分散到其他工作日");
        }
        if (classOverloadCount > 0) {
            suggestions.add("建议减少课程过于集中的班级课时，将部分课程调整到其他日期");
        }
        if (fridayAfternoonCount > 0) {
            suggestions.add("建议减少周五下午课程，将部分课程调整到上午或其他工作日");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("当前课表质量良好，暂无需要优化的项目");
        }
        return suggestions;
    }

    private boolean isRoomTypeMismatch(Course course, Classroom classroom) {
        if (CourseType.EXPERIMENT.getCode().equals(course.getCourseType()) && !RoomType.LAB.getCode().equals(classroom.getRoomType())) return true;
        return CourseType.COMPUTER.getCode().equals(course.getCourseType()) && !RoomType.COMPUTER.getCode().equals(classroom.getRoomType());
    }

    /**
     * 当前系统以“大节”为排课单位，默认 2 课时折算 1 个大节。
     * 因此奇数课时需要向上取整，保证任务不会少排。
     */
    private int calculateRequiredSlots(TeachingTask task) {
        if (task == null || task.getWeeklyHours() == null) return 0;
        return (int) Math.ceil(task.getWeeklyHours() / 2.0);
    }

    private String buildPairKey(Long left, Long right) {
        return left + "_" + right;
    }

    private String buildDailyKey(Long objectId, Integer dayOfWeek) {
        return objectId + "_" + dayOfWeek;
    }

    private Integer getDayOfWeek(TimeSlot slot) {
        return slot != null ? slot.getDayOfWeek() : null;
    }

    private static class Context {
        private List<Schedule> schedules;
        private Map<Long, TeachingTask> taskMap;
        private Map<Long, ClassInfo> classMap;
        private Map<Long, Classroom> classroomMap;
        private Map<Long, Course> courseMap;
        private Map<Long, TimeSlot> timeSlotMap;
        private Map<String, TeacherUnavailableTime> unavailableMap;
        private int teacherDailyLimit;
        private int classDailyLimit;
    }

    @Data
    public static class ScoreResult {
        private int score;
        private String grade;
        private String gradeName;
        private int conflictCount;
        private int unfinishedTaskCount;
        private int teacherOverloadCount;
        private int classOverloadCount;
        private int fridayAfternoonCount;
        private List<String> deductionDetail;
        private List<String> suggestion;
    }
}
