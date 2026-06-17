package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleUnassignedTask;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V9 阶段 3C 端到端集成测试（V9_05 T9 V8 引擎单双周）。
 *
 * <p>验证 SOLVER_V8 在含 ODD/EVEN/ALL 混合任务的数据集上端到端正确工作：
 * <ul>
 *   <li>全部排下（0 unassigned）</li>
 *   <li>落库 plan_item.weekType 正确透传（与 TeachingTask.weekType 一致）</li>
 *   <li>ODD + EVEN 同物理时段共槽（单双周核心价值）</li>
 *   <li>同 seed 同数据可复现（两次 solve 方案完全一致）</li>
 *   <li>硬约束保持（无教师/班级/教室/时段硬冲突）</li>
 * </ul>
 *
 * <p>数据隔离遵循 CLAUDE.md：唯一后缀（{@code System.currentTimeMillis()}）、独立学期、容量/房型匹配、
 * tearDown 物理清理。
 */
@SpringBootTest
class EngineWeekTypeIntegrationTest {

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

    private final String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
    private final List<Long> planIds = new ArrayList<>();
    private Long semesterId;
    private Long allTeacherId;
    private Long oddTeacherId;
    private Long evenTeacherId;
    private Long classId;
    private Long allCourseId;
    private Long oddCourseId;
    private Long evenCourseId;
    private Long classroomId;
    private final List<Long> taskIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long planId : planIds) {
            unassignedTaskMapper.delete(new LambdaQueryWrapper<ScheduleUnassignedTask>()
                    .eq(ScheduleUnassignedTask::getPlanId, planId));
            planItemMapper.delete(new LambdaQueryWrapper<SchedulePlanItem>()
                    .eq(SchedulePlanItem::getPlanId, planId));
            planMapper.deleteById(planId);
        }
        for (Long id : taskIds) {
            teachingTaskMapper.deleteById(id);
        }
        if (classroomId != null) {
            classroomMapper.deleteById(classroomId);
        }
        for (Long id : java.util.Arrays.asList(allCourseId, oddCourseId, evenCourseId)) {
            if (id != null) {
                courseMapper.deleteById(id);
            }
        }
        if (classId != null) {
            classInfoMapper.deleteById(classId);
        }
        for (Long id : java.util.Arrays.asList(allTeacherId, oddTeacherId, evenTeacherId)) {
            if (id != null) {
                teacherMapper.deleteById(id);
            }
        }
        if (semesterId != null) {
            semesterMapper.deleteById(semesterId);
        }
    }

    /**
     * 端到端：混合 weekType 数据集，SOLVER_V8 全部排下，plan_item.weekType 正确透传。
     *
     * <p>数据集设计：
     * <ul>
     *   <li>3 个教师 × 3 门课 × 1 个班，每个教师各承担 ALL/ODD/EVEN 一门，每周 2 课时</li>
     *   <li>教室容量充足（NORMAL/60）、班级人数 30</li>
     *   <li>time_slot 基线表充足（周一~周五每天 5 个物理大节）</li>
     * </ul>
     */
    @Test
    void solverV8SchedulesMixedWeekTypeTasksWithZeroUnassigned() {
        assertFalse(timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()).isEmpty(),
                "time_slot must contain baseline slots");
        createMixedWeekTypeDataset();

        ScheduleGenerateResult result = generate("V9_MIX_" + suffix);
        planIds.add(result.getPlanId());

        // 3 个任务全部排下
        assertEquals(0, result.getUnscheduledCount(),
                "混合 weekType 数据集 SOLVER_V8 应全部排下");
        assertEquals(3, result.getScheduledCount());

        // 落库校验
        List<SchedulePlanItem> items = planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, result.getPlanId()));
        assertEquals(3, items.size());

        // weekType 透传：每个 item 的 weekType 应与对应 TeachingTask 一致
        Map<Long, String> taskToWeekType = new HashMap<>();
        for (Long id : taskIds) {
            TeachingTask t = teachingTaskMapper.selectById(id);
            taskToWeekType.put(id, WeekTypeSupport.normalize(t.getWeekType()));
        }
        for (SchedulePlanItem item : items) {
            String expected = taskToWeekType.get(item.getTeachingTaskId());
            assertNotNull(expected, "plan_item.teachingTaskId 应映射到已知任务");
            assertEquals(expected, WeekTypeSupport.normalize(item.getWeekType()),
                    "plan_item.weekType 应透传 TeachingTask.weekType");
        }

        // 至少覆盖三种 weekType
        List<String> itemWeekTypes = items.stream()
                .map(i -> WeekTypeSupport.normalize(i.getWeekType()))
                .sorted().toList();
        assertTrue(itemWeekTypes.contains(WeekTypeSupport.ALL), "数据集应排下 ALL 任务");
        assertTrue(itemWeekTypes.contains(WeekTypeSupport.ODD), "数据集应排下 ODD 任务");
        assertTrue(itemWeekTypes.contains(WeekTypeSupport.EVEN), "数据集应排下 EVEN 任务");

        // 无 unassigned 落库
        List<ScheduleUnassignedTask> unassigned = unassignedTaskMapper.selectList(
                new LambdaQueryWrapper<ScheduleUnassignedTask>().eq(ScheduleUnassignedTask::getPlanId, result.getPlanId()));
        assertTrue(unassigned.isEmpty(), "不应有 unassigned 记录");

        // 硬约束保持：同教师/同班/同教室同一物理时段不得有 weekType 重叠冲突
        assertNoHardConflict(items);
    }

    /**
     * ODD + EVEN 同物理时段共槽（单双周核心价值）：
     * 一个班、两个教师、两门课，一个 ODD 一个 EVEN，两者应能排到同一物理时段（共用时段）。
     *
     * <p>由于 SOLVER_V8 是优化器不强制共槽，这里仅验证可行性：混合 ODD+EVEN 任务全部排下且无硬冲突，
     * 且 plan_item 上 ODD/EVEN 各自的 weekType 正确。共槽语义已在 InMemoryConflictDetectorWeekTypeTest 单测验证。
     */
    @Test
    void solverV8SchedulesOddEvenTasksThatCanShareSlot() {
        assertFalse(timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()).isEmpty(),
                "time_slot must contain baseline slots");
        createOddEvenShareableDataset();

        ScheduleGenerateResult result = generate("V9_SHARE_" + suffix);
        planIds.add(result.getPlanId());

        assertEquals(0, result.getUnscheduledCount(), "ODD+EVEN 任务应全部排下");

        List<SchedulePlanItem> items = planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, result.getPlanId()));
        assertEquals(2, items.size());

        // 一个 ODD 一个 EVEN
        List<String> weekTypes = items.stream()
                .map(i -> WeekTypeSupport.normalize(i.getWeekType())).sorted().toList();
        assertEquals(List.of(WeekTypeSupport.EVEN, WeekTypeSupport.ODD), weekTypes,
                "应恰好有一个 ODD 和一个 EVEN plan_item");

        assertNoHardConflict(items);
    }

    /**
     * T9 可复现性：同 seed 同数据两次 solve，方案完全一致（assignment 签名相同）。
     *
     * <p>退火（annealing）按 wall-clock 时间预算停机，受机器负载影响，多任务跨次运行步数不同 →
     * 结果天然不可复现（这与 V8 一致，V8 的 {@code V8SolverGenerateIntegrationTest.solverV8SameSeed...}
     * 也只放 1 个任务：单 assignment 触发 {@code assignments.size() < 2} 短路，退火直接返回 feasible）。
     *
     * <p>本测试对齐 V8 范式：单任务（ODD weekType）数据集，退火短路，只跑确定性回溯（BacktrackingSolver
     * 无随机源，{@link EngineFacade#solve} 的 random 仅用于退火）。两次 solve 应产出完全一致的方案。
     *
     * <p>退火自身的种子可复现性在 {@code AnnealingOptimizerTest}（纯引擎、固定预算）已覆盖。
     */
    @Test
    void solverV8SameSeedGeneratesIdenticalPlanForWeekTypeData() {
        assertFalse(timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()).isEmpty(),
                "time_slot must contain baseline slots");
        createSingleOddTaskDataset();

        ScheduleGenerateResult first = generate("V9_REP_A_" + suffix);
        planIds.add(first.getPlanId());
        ScheduleGenerateResult second = generate("V9_REP_B_" + suffix);
        planIds.add(second.getPlanId());

        assertEquals(0, first.getUnscheduledCount());
        assertEquals(0, second.getUnscheduledCount());
        assertEquals(planItemSignature(first.getPlanId()), planItemSignature(second.getPlanId()),
                "同 seed 同数据（单 ODD 任务）两次 solve 方案应完全一致（含 weekType 维度）");
    }

    // ---------- helpers ----------

    private ScheduleGenerateResult generate(String planName) {
        ScheduleGenerateRequest request = new ScheduleGenerateRequest();
        request.setSemesterId(semesterId);
        request.setStrategyType("SOLVER_V8");
        request.setPlanName(planName);
        request.setOverwriteDraft(true);
        request.setSolverSeed(42L);
        request.setSolverTimeBudgetMs(1_000L);
        return generateService.generate(request);
    }

    /**
     * 混合 weekType 数据集：3 教师 3 课 1 班，每个教师各一门课（ALL/ODD/EVEN），每周 2 课时。
     * 教师/课分离避免互相干扰，班级共享但时段充足，3 个任务必能排下。
     */
    private void createMixedWeekTypeDataset() {
        Semester semester = new Semester();
        semester.setName("V9_MIX_SEM_" + suffix);
        semester.setSchoolYear("2026-2027");
        semester.setTerm("1");
        semester.setIsCurrent(0);
        semesterMapper.insert(semester);
        semesterId = semester.getId();

        allTeacherId = insertTeacher("V9_ALL_T_" + suffix, "ALL教师");
        oddTeacherId = insertTeacher("V9_ODD_T_" + suffix, "ODD教师");
        evenTeacherId = insertTeacher("V9_EVEN_T_" + suffix, "EVEN教师");

        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName("V9_MIX班" + suffix);
        classInfo.setMajor("测试专业");
        classInfo.setGrade("2026");
        classInfo.setStudentCount(30);
        classInfo.setStatus(1);
        classInfo.setDeleted(0);
        classInfoMapper.insert(classInfo);
        classId = classInfo.getId();

        allCourseId = insertCourse("V9_ALL_C_" + suffix, "ALL课程");
        oddCourseId = insertCourse("V9_ODD_C_" + suffix, "ODD课程");
        evenCourseId = insertCourse("V9_EVEN_C_" + suffix, "EVEN课程");

        Classroom room = new Classroom();
        room.setRoomName("V9_MIX_ROOM_" + suffix);
        room.setBuilding("测试楼");
        room.setCapacity(60);
        room.setRoomType("NORMAL");
        room.setStatus(1);
        room.setDeleted(0);
        classroomMapper.insert(room);
        classroomId = room.getId();

        taskIds.add(insertTask(semesterId, allTeacherId, classId, allCourseId, "ALL"));
        taskIds.add(insertTask(semesterId, oddTeacherId, classId, oddCourseId, "ODD"));
        taskIds.add(insertTask(semesterId, evenTeacherId, classId, evenCourseId, "EVEN"));
    }

    /**
     * ODD+EVEN 共槽数据集：1 班 2 教师 2 课，一 ODD 一 EVEN。
     */
    private void createOddEvenShareableDataset() {
        Semester semester = new Semester();
        semester.setName("V9_SHARE_SEM_" + suffix);
        semester.setSchoolYear("2026-2027");
        semester.setTerm("1");
        semester.setIsCurrent(0);
        semesterMapper.insert(semester);
        semesterId = semester.getId();

        oddTeacherId = insertTeacher("V9_SH_ODD_T_" + suffix, "共享ODD教师");
        evenTeacherId = insertTeacher("V9_SH_EVEN_T_" + suffix, "共享EVEN教师");

        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName("V9_SHARE班" + suffix);
        classInfo.setMajor("测试专业");
        classInfo.setGrade("2026");
        classInfo.setStudentCount(30);
        classInfo.setStatus(1);
        classInfo.setDeleted(0);
        classInfoMapper.insert(classInfo);
        classId = classInfo.getId();

        oddCourseId = insertCourse("V9_SH_ODD_C_" + suffix, "共享ODD课");
        evenCourseId = insertCourse("V9_SH_EVEN_C_" + suffix, "共享EVEN课");

        Classroom room = new Classroom();
        room.setRoomName("V9_SHARE_ROOM_" + suffix);
        room.setBuilding("测试楼");
        room.setCapacity(60);
        room.setRoomType("NORMAL");
        room.setStatus(1);
        room.setDeleted(0);
        classroomMapper.insert(room);
        classroomId = room.getId();

        taskIds.add(insertTask(semesterId, oddTeacherId, classId, oddCourseId, "ODD"));
        taskIds.add(insertTask(semesterId, evenTeacherId, classId, evenCourseId, "EVEN"));
    }

    /**
     * 单 ODD 任务数据集（可复现性用）：1 教师 1 课 1 班 1 教室，唯一 ODD 任务。
     * 单 assignment 触发退火短路（assignments.size() < 2），只剩确定性回溯，同 seed 同数据必复现。
     */
    private void createSingleOddTaskDataset() {
        Semester semester = new Semester();
        semester.setName("V9_REP_SEM_" + suffix);
        semester.setSchoolYear("2026-2027");
        semester.setTerm("1");
        semester.setIsCurrent(0);
        semesterMapper.insert(semester);
        semesterId = semester.getId();

        oddTeacherId = insertTeacher("V9_REP_ODD_T_" + suffix, "可复现ODD教师");

        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName("V9_REP班" + suffix);
        classInfo.setMajor("测试专业");
        classInfo.setGrade("2026");
        classInfo.setStudentCount(30);
        classInfo.setStatus(1);
        classInfo.setDeleted(0);
        classInfoMapper.insert(classInfo);
        classId = classInfo.getId();

        oddCourseId = insertCourse("V9_REP_ODD_C_" + suffix, "可复现ODD课");

        Classroom room = new Classroom();
        room.setRoomName("V9_REP_ROOM_" + suffix);
        room.setBuilding("测试楼");
        room.setCapacity(60);
        room.setRoomType("NORMAL");
        room.setStatus(1);
        room.setDeleted(0);
        classroomMapper.insert(room);
        classroomId = room.getId();

        taskIds.add(insertTask(semesterId, oddTeacherId, classId, oddCourseId, "ODD"));
    }

    private Long insertTeacher(String teacherNo, String name) {
        Teacher t = new Teacher();
        t.setTeacherNo(teacherNo);
        t.setName(name);
        t.setDepartment("测试系");
        t.setStatus(1);
        t.setDeleted(0);
        teacherMapper.insert(t);
        return t.getId();
    }

    private Long insertCourse(String courseNo, String courseName) {
        Course c = new Course();
        c.setCourseNo(courseNo);
        c.setCourseName(courseName);
        c.setCourseType("NORMAL");
        c.setWeeklyHours(2);
        c.setDeleted(0);
        courseMapper.insert(c);
        return c.getId();
    }

    private Long insertTask(Long semesterId, Long teacherId, Long classId, Long courseId, String weekType) {
        TeachingTask t = new TeachingTask();
        t.setSemesterId(semesterId);
        t.setTeacherId(teacherId);
        t.setClassId(classId);
        t.setCourseId(courseId);
        t.setWeeklyHours(2);
        t.setStatus(1);
        t.setDeleted(0);
        t.setWeekType(weekType);
        teachingTaskMapper.insert(t);
        return t.getId();
    }

    /**
     * 硬约束保持校验：同一物理时段（weekday + startPeriod）下，同教师/同班/同教室的资源，
     * weekType 不得两两重叠（违反 WeekTypeSupport.overlap 矩阵即硬冲突）。
     */
    private void assertNoHardConflict(List<SchedulePlanItem> items) {
        // 按 (resource_type, resource_id, weekday, startPeriod) 分组
        Map<String, List<SchedulePlanItem>> byTeacher = groupByResource(items, SchedulePlanItem::getTeacherId, "teacher");
        Map<String, List<SchedulePlanItem>> byClass = groupByResource(items, SchedulePlanItem::getClassId, "class");
        Map<String, List<SchedulePlanItem>> byRoom = groupByResource(items, SchedulePlanItem::getClassroomId, "room");
        assertNoOverlap(byTeacher, "teacher");
        assertNoOverlap(byClass, "class");
        assertNoOverlap(byRoom, "room");
    }

    private Map<String, List<SchedulePlanItem>> groupByResource(List<SchedulePlanItem> items,
                                                                 java.util.function.Function<SchedulePlanItem, Long> keyFn,
                                                                 String type) {
        return items.stream().collect(Collectors.groupingBy(
                item -> type + ":" + keyFn.apply(item) + ":" + item.getWeekday() + ":" + item.getStartPeriod()));
    }

    private void assertNoOverlap(Map<String, List<SchedulePlanItem>> groups, String type) {
        for (Map.Entry<String, List<SchedulePlanItem>> e : groups.entrySet()) {
            List<SchedulePlanItem> slotItems = e.getValue();
            if (slotItems.size() < 2) continue;
            for (int i = 0; i < slotItems.size(); i++) {
                for (int j = i + 1; j < slotItems.size(); j++) {
                    String a = WeekTypeSupport.normalize(slotItems.get(i).getWeekType());
                    String b = WeekTypeSupport.normalize(slotItems.get(j).getWeekType());
                    assertFalse(WeekTypeSupport.overlap(a, b),
                            type + " 硬冲突：" + e.getKey() + " 同时段 " + a + " 与 " + b + " 重叠");
                }
            }
        }
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
                        + ":" + item.getEndPeriod()
                        + ":" + WeekTypeSupport.normalize(item.getWeekType()))
                .sorted()
                .toList();
    }
}
