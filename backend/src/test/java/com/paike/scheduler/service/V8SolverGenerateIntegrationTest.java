package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.PerformanceBaselineRecord;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.entity.ScheduleScoreReport;
import com.paike.scheduler.entity.ScheduleUnassignedTask;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.PerformanceBaselineRecordMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleScoreDetailMapper;
import com.paike.scheduler.mapper.ScheduleScoreReportMapper;
import com.paike.scheduler.mapper.ScheduleUnassignedTaskMapper;
import com.paike.scheduler.mapper.SemesterMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.dto.ScheduleGenerateRequest;
import com.paike.scheduler.service.dto.ScheduleGenerateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class V8SolverGenerateIntegrationTest {

    @Autowired private V3ScheduleGenerateService generateService;
    @Autowired private SemesterMapper semesterMapper;
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private ClassInfoMapper classInfoMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private ClassroomMapper classroomMapper;
    @Autowired private TeachingTaskMapper teachingTaskMapper;
    @Autowired private TimeSlotMapper timeSlotMapper;
    @Autowired private SchedulePlanMapper planMapper;
    @Autowired private SchedulePlanItemMapper planItemMapper;
    @Autowired private ScheduleUnassignedTaskMapper unassignedTaskMapper;
    @Autowired private PerformanceBaselineRecordMapper performanceMapper;
    @Autowired private ScheduleScoreReportMapper scoreReportMapper;
    @Autowired private ScheduleScoreDetailMapper scoreDetailMapper;

    private final String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
    private final List<Long> planIds = new ArrayList<>();
    private Long semesterId;
    private Long teacherId;
    private Long classId;
    private Long courseId;
    private Long classroomId;
    private Long taskId;

    @AfterEach
    void tearDown() {
        for (Long planId : planIds) {
            scoreDetailMapper.delete(new LambdaQueryWrapper<ScheduleScoreDetail>()
                    .eq(ScheduleScoreDetail::getPlanId, planId));
            performanceMapper.delete(new LambdaQueryWrapper<PerformanceBaselineRecord>()
                    .eq(PerformanceBaselineRecord::getPlanId, planId));
            unassignedTaskMapper.delete(new LambdaQueryWrapper<ScheduleUnassignedTask>()
                    .eq(ScheduleUnassignedTask::getPlanId, planId));
            planItemMapper.delete(new LambdaQueryWrapper<SchedulePlanItem>()
                    .eq(SchedulePlanItem::getPlanId, planId));
            planMapper.deleteById(planId);
        }
        if (semesterId != null) {
            scoreReportMapper.delete(new LambdaQueryWrapper<ScheduleScoreReport>()
                    .eq(ScheduleScoreReport::getSemesterId, semesterId));
        }
        if (taskId != null) {
            teachingTaskMapper.deleteById(taskId);
        }
        if (classroomId != null) {
            classroomMapper.deleteById(classroomId);
        }
        if (courseId != null) {
            courseMapper.deleteById(courseId);
        }
        if (classId != null) {
            classInfoMapper.deleteById(classId);
        }
        if (teacherId != null) {
            teacherMapper.deleteById(teacherId);
        }
        if (semesterId != null) {
            semesterMapper.deleteById(semesterId);
        }
    }

    @Test
    void solverV8GeneratesPersistedPlanAndOldComprehensiveStillWorks() {
        assertFalse(timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()).isEmpty(),
                "time_slot must contain baseline slots");
        createSolvableDataset();

        ScheduleGenerateResult solverResult = generate("SOLVER_V8", "V8_IT_SOLVER_" + suffix);
        planIds.add(solverResult.getPlanId());

        assertEquals("SOLVER_V8", solverResult.getStrategyType());
        assertEquals(1, solverResult.getScheduledCount());
        assertEquals(0, solverResult.getUnscheduledCount());
        assertPersistedPlan(solverResult.getPlanId(), "SOLVER_V8");
        assertSolverPerformanceRecorded(solverResult.getPlanId());

        ScheduleGenerateResult oldResult = generate("COMPREHENSIVE", "V8_IT_OLD_" + suffix);
        planIds.add(oldResult.getPlanId());

        assertEquals("COMPREHENSIVE", oldResult.getStrategyType());
        assertEquals(1, oldResult.getScheduledCount());
        assertEquals(0, oldResult.getUnscheduledCount());
        assertPersistedPlan(oldResult.getPlanId(), "COMPREHENSIVE");
    }

    @Test
    void solverV8SameSeedGeneratesIdenticalPersistedPlanItems() {
        assertFalse(timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()).isEmpty(),
                "time_slot must contain baseline slots");
        createSolvableDataset();

        ScheduleGenerateResult first = generate("SOLVER_V8", "V8_IT_SEED_A_" + suffix);
        planIds.add(first.getPlanId());
        ScheduleGenerateResult second = generate("SOLVER_V8", "V8_IT_SEED_B_" + suffix);
        planIds.add(second.getPlanId());

        assertEquals(planItemSignature(first.getPlanId()), planItemSignature(second.getPlanId()));
    }

    private ScheduleGenerateResult generate(String strategyType, String planName) {
        ScheduleGenerateRequest request = new ScheduleGenerateRequest();
        request.setSemesterId(semesterId);
        request.setStrategyType(strategyType);
        request.setPlanName(planName);
        request.setOverwriteDraft(true);
        request.setSolverSeed(42L);
        request.setSolverTimeBudgetMs(1_000L);
        return generateService.generate(request);
    }

    private void createSolvableDataset() {
        Semester semester = new Semester();
        semester.setName("V8_IT_SEM_" + suffix);
        semester.setSchoolYear("2026-2027");
        semester.setTerm("1");
        semester.setIsCurrent(0);
        semesterMapper.insert(semester);
        semesterId = semester.getId();

        Teacher teacher = new Teacher();
        teacher.setTeacherNo("V8T_" + suffix);
        teacher.setName("V8集成教师");
        teacher.setDepartment("测试系");
        teacher.setStatus(1);
        teacher.setDeleted(0);
        teacherMapper.insert(teacher);
        teacherId = teacher.getId();

        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName("V8集成班级" + suffix);
        classInfo.setMajor("测试专业");
        classInfo.setGrade("2026");
        classInfo.setStudentCount(30);
        classInfo.setStatus(1);
        classInfo.setDeleted(0);
        classInfoMapper.insert(classInfo);
        classId = classInfo.getId();

        Course course = new Course();
        course.setCourseNo("V8C_" + suffix);
        course.setCourseName("V8集成课程");
        course.setCourseType("NORMAL");
        course.setWeeklyHours(2);
        course.setDeleted(0);
        courseMapper.insert(course);
        courseId = course.getId();

        Classroom classroom = new Classroom();
        classroom.setRoomName("V8_ROOM_" + suffix);
        classroom.setBuilding("测试楼");
        classroom.setCapacity(60);
        classroom.setRoomType("NORMAL");
        classroom.setStatus(1);
        classroom.setDeleted(0);
        classroomMapper.insert(classroom);
        classroomId = classroom.getId();

        TeachingTask task = new TeachingTask();
        task.setSemesterId(semesterId);
        task.setTeacherId(teacherId);
        task.setClassId(classId);
        task.setCourseId(courseId);
        task.setWeeklyHours(2);
        task.setStatus(1);
        task.setDeleted(0);
        teachingTaskMapper.insert(task);
        taskId = task.getId();
    }

    private void assertPersistedPlan(Long planId, String strategyType) {
        SchedulePlan plan = planMapper.selectById(planId);
        assertNotNull(plan);
        assertEquals(strategyType, plan.getStrategyType());
        assertEquals(1, plan.getScheduledCount());
        assertEquals(0, plan.getUnscheduledCount());
        assertNotNull(plan.getTotalScore());

        List<SchedulePlanItem> items = planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, planId));
        assertEquals(1, items.size());
        SchedulePlanItem item = items.get(0);
        assertEquals(taskId, item.getTeachingTaskId());
        assertEquals(teacherId, item.getTeacherId());
        assertEquals(classId, item.getClassId());
        assertEquals(courseId, item.getCourseId());
        assertNotNull(item.getClassroomId());
        assertTrue(item.getStartPeriod() % 2 == 1);
        assertEquals(item.getStartPeriod() + 1, item.getEndPeriod());

        List<ScheduleUnassignedTask> unassigned = unassignedTaskMapper.selectList(
                new LambdaQueryWrapper<ScheduleUnassignedTask>().eq(ScheduleUnassignedTask::getPlanId, planId));
        assertTrue(unassigned.isEmpty());
    }

    private void assertSolverPerformanceRecorded(Long planId) {
        List<PerformanceBaselineRecord> records = performanceMapper.selectList(
                new LambdaQueryWrapper<PerformanceBaselineRecord>()
                        .eq(PerformanceBaselineRecord::getPlanId, planId)
                        .eq(PerformanceBaselineRecord::getOperationType, PerformanceBaselineService.OP_V8_SOLVER_GENERATE));
        assertEquals(1, records.size());
        PerformanceBaselineRecord record = records.get(0);
        assertEquals(1, record.getSuccess());
        assertTrue(record.getExtraJson().contains("\"seed\":42"));
        assertTrue(record.getExtraJson().contains("\"timeBudgetMs\":1000"));
        assertTrue(record.getExtraJson().contains("\"optimizeTimeBudgetMs\":10000"));
        assertTrue(record.getExtraJson().contains("\"backtracks\":"));
        assertTrue(record.getExtraJson().contains("\"annealingSteps\":"));
        assertTrue(record.getExtraJson().contains("\"initialScore\":"));
        assertTrue(record.getExtraJson().contains("\"finalScore\":"));
    }

    private List<String> planItemSignature(Long planId) {
        return planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, planId))
                .stream()
                .map(item -> item.getTeachingTaskId()
                        + ":" + item.getTeacherId()
                        + ":" + item.getClassId()
                        + ":" + item.getCourseId()
                        + ":" + item.getClassroomId()
                        + ":" + item.getWeekday()
                        + ":" + item.getStartPeriod()
                        + ":" + item.getEndPeriod())
                .sorted()
                .toList();
    }
}
