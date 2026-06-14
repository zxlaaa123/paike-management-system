package com.paike.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.common.enums.SchedulePlanStatus;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleAdjustLogMapper;
import com.paike.scheduler.mapper.ScheduleConsistencyCheckMapper;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.vo.V5ConsistencyCheckReportVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class V5ConsistencyCheckServiceTest {

    private ScheduleRepairTaskMapper repairTaskMapper;
    private SchedulePlanMapper planMapper;
    private SchedulePlanItemMapper planItemMapper;
    private ScheduleMapper scheduleMapper;
    private ScheduleLockedItemMapper lockedItemMapper;
    private ScheduleAdjustLogMapper adjustLogMapper;
    private ScheduleConsistencyCheckMapper consistencyCheckMapper;
    private TeacherMapper teacherMapper;
    private ClassInfoMapper classInfoMapper;
    private ClassroomMapper classroomMapper;
    private CourseMapper courseMapper;
    private TimeSlotMapper timeSlotMapper;
    private TeacherUnavailableTimeService unavailableTimeService;
    private V5ConsistencyCheckService service;

    @BeforeEach
    void setUp() {
        repairTaskMapper = mock(ScheduleRepairTaskMapper.class);
        planMapper = mock(SchedulePlanMapper.class);
        planItemMapper = mock(SchedulePlanItemMapper.class);
        scheduleMapper = mock(ScheduleMapper.class);
        lockedItemMapper = mock(ScheduleLockedItemMapper.class);
        adjustLogMapper = mock(ScheduleAdjustLogMapper.class);
        consistencyCheckMapper = mock(ScheduleConsistencyCheckMapper.class);
        teacherMapper = mock(TeacherMapper.class);
        classInfoMapper = mock(ClassInfoMapper.class);
        classroomMapper = mock(ClassroomMapper.class);
        courseMapper = mock(CourseMapper.class);
        timeSlotMapper = mock(TimeSlotMapper.class);
        unavailableTimeService = mock(TeacherUnavailableTimeService.class);
        service = new V5ConsistencyCheckService(
                repairTaskMapper,
                planMapper,
                planItemMapper,
                scheduleMapper,
                lockedItemMapper,
                adjustLogMapper,
                consistencyCheckMapper,
                teacherMapper,
                classInfoMapper,
                classroomMapper,
                courseMapper,
                timeSlotMapper,
                unavailableTimeService,
                new ObjectMapper());
    }

    @Test
    void check_allowsNonOverlappingItemsWithSameResources() {
        V5ConsistencyCheckReportVo report = runCheck(List.of(
                item(1L, 101L, 1L, 1L, 1L, 1, 1, 2),
                item(2L, 102L, 1L, 1L, 1L, 1, 3, 4)));

        assertEquals("PASS", report.getStatus());
        assertEquals(0, report.getBlockingIssueCount());
        assertTrue(report.getIssues().isEmpty());
    }

    @Test
    void check_reportsTeacherClassAndClassroomHardConflictsForOverlappingItems() {
        V5ConsistencyCheckReportVo report = runCheck(List.of(
                item(1L, 101L, 1L, 1L, 1L, 1, 1, 2),
                item(2L, 102L, 1L, 1L, 1L, 1, 2, 3)));

        List<String> codes = report.getIssues().stream()
                .map(issue -> issue.getCode())
                .toList();

        assertEquals("FAIL", report.getStatus());
        assertEquals(3, report.getBlockingIssueCount());
        assertTrue(codes.contains("TEACHER_HARD_CONFLICT"));
        assertTrue(codes.contains("CLASS_HARD_CONFLICT"));
        assertTrue(codes.contains("CLASSROOM_HARD_CONFLICT"));
    }

    @Test
    void check_highOverlapUniqueResourcesCompletesWithinCurrentScale() {
        List<SchedulePlanItem> items = IntStream.rangeClosed(1, 200)
                .mapToObj(i -> item((long) i, 1000L + i, 2000L + i, 3000L + i, 4000L + i, 1, 1, 2))
                .toList();

        V5ConsistencyCheckReportVo report = assertTimeout(Duration.ofSeconds(2), () -> runCheck(items));

        assertEquals("PASS", report.getStatus());
        assertTrue(report.getIssues().isEmpty());
    }

    /**
     * V9 阶段 2C T7 核心：单双周共槽（ODD+EVEN）合法，不误报硬冲突。
     * 教师/班级/教室相同、时段重叠，但 weekType ODD vs EVEN → 不冲突 → PASS。
     */
    @Test
    void check_oddEvenSharedSlotNotReportedAsConflict() {
        V5ConsistencyCheckReportVo report = runCheck(List.of(
                item(1L, 101L, 1L, 1L, 1L, 1, 1, 2, "ODD"),
                item(2L, 102L, 1L, 1L, 1L, 1, 1, 2, "EVEN")));

        assertEquals("PASS", report.getStatus());
        assertEquals(0, report.getBlockingIssueCount());
        assertTrue(report.getIssues().isEmpty(),
                "ODD+EVEN 共槽应合法，实际 issues: " + report.getIssues());
    }

    /**
     * V9 阶段 2C：ALL 与任意 weekType 冲突（ALL 占满整个时段）。
     * ALL + ODD 同槽同资源 → 仍报 3 个硬冲突。
     */
    @Test
    void check_allOverlapsWithOddStillReportedAsConflict() {
        V5ConsistencyCheckReportVo report = runCheck(List.of(
                item(1L, 101L, 1L, 1L, 1L, 1, 1, 2, "ALL"),
                item(2L, 102L, 1L, 1L, 1L, 1, 1, 2, "ODD")));

        List<String> codes = report.getIssues().stream()
                .map(issue -> issue.getCode())
                .toList();

        assertEquals("FAIL", report.getStatus());
        assertEquals(3, report.getBlockingIssueCount());
        assertTrue(codes.contains("TEACHER_HARD_CONFLICT"));
        assertTrue(codes.contains("CLASS_HARD_CONFLICT"));
        assertTrue(codes.contains("CLASSROOM_HARD_CONFLICT"));
    }

    private V5ConsistencyCheckReportVo runCheck(List<SchedulePlanItem> simulationItems) {
        ScheduleRepairTask task = new ScheduleRepairTask();
        task.setId(1L);
        task.setSemesterId(1L);

        SchedulePlan plan = plan(10L);
        SchedulePlan source = plan(20L);
        source.setRepairTaskId(null);
        source.setSourcePlanId(null);

        when(repairTaskMapper.selectById(1L)).thenReturn(task);
        when(planMapper.selectById(10L)).thenReturn(plan);
        when(planMapper.selectById(20L)).thenReturn(source);
        when(planItemMapper.selectList(any())).thenReturn(simulationItems, List.of());
        when(lockedItemMapper.selectList(any())).thenReturn(List.of());
        when(adjustLogMapper.selectList(any())).thenReturn(List.of());
        when(timeSlotMapper.selectList(any())).thenReturn(List.of());
        when(scheduleMapper.selectCount(any())).thenReturn(0L);
        when(classroomMapper.selectById(anyLong())).thenAnswer(invocation -> room(invocation.getArgument(0)));
        when(teacherMapper.selectById(anyLong())).thenAnswer(invocation -> teacher(invocation.getArgument(0)));
        when(classInfoMapper.selectById(anyLong())).thenAnswer(invocation -> classInfo(invocation.getArgument(0)));
        when(courseMapper.selectById(anyLong())).thenAnswer(invocation -> course(invocation.getArgument(0)));

        return service.check(1L, 10L, false);
    }

    private SchedulePlan plan(Long id) {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(id);
        plan.setRepairTaskId(1L);
        plan.setSourcePlanId(20L);
        plan.setSemesterId(1L);
        plan.setPlanMode("SIMULATION");
        plan.setStatus(SchedulePlanStatus.SIMULATION.getCode());
        return plan;
    }

    private SchedulePlanItem item(Long id, Long teachingTaskId, Long teacherId, Long classId, Long classroomId,
                                  Integer weekday, Integer startPeriod, Integer endPeriod) {
        return item(id, teachingTaskId, teacherId, classId, classroomId, weekday, startPeriod, endPeriod, null);
    }

    /** V9 单双周：带 weekType 的 item 构造（null 视为 ALL，向后兼容现有 fixture） */
    private SchedulePlanItem item(Long id, Long teachingTaskId, Long teacherId, Long classId, Long classroomId,
                                  Integer weekday, Integer startPeriod, Integer endPeriod, String weekType) {
        SchedulePlanItem item = new SchedulePlanItem();
        item.setId(id);
        item.setPlanId(10L);
        item.setSemesterId(1L);
        item.setTeachingTaskId(teachingTaskId);
        item.setTeacherId(teacherId);
        item.setClassId(classId);
        item.setCourseId(1L);
        item.setClassroomId(classroomId);
        item.setWeekday(weekday);
        item.setStartPeriod(startPeriod);
        item.setEndPeriod(endPeriod);
        item.setWeekType(weekType);
        return item;
    }

    private Teacher teacher(Long id) {
        Teacher teacher = new Teacher();
        teacher.setId(id);
        teacher.setName("教师" + id);
        return teacher;
    }

    private ClassInfo classInfo(Long id) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(id);
        classInfo.setClassName("班级" + id);
        classInfo.setStudentCount(30);
        return classInfo;
    }

    private Classroom room(Long id) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setRoomName("教室" + id);
        classroom.setCapacity(50);
        classroom.setRoomType(RoomType.NORMAL.getCode());
        return classroom;
    }

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        course.setCourseName("课程" + id);
        course.setCourseType(CourseType.NORMAL.getCode());
        return course;
    }
}
