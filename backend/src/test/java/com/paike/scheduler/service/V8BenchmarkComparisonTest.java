package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineSolution;
import com.paike.scheduler.engine.solver.EngineFacade;
import com.paike.scheduler.engine.solver.SolverConfig;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.PerformanceBaselineRecord;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.entity.ScheduleScoreReport;
import com.paike.scheduler.entity.ScheduleUnassignedTask;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.PerformanceBaselineRecordMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRuleWeightMapper;
import com.paike.scheduler.mapper.ScheduleScoreDetailMapper;
import com.paike.scheduler.mapper.ScheduleScoreReportMapper;
import com.paike.scheduler.mapper.ScheduleUnassignedTaskMapper;
import com.paike.scheduler.mapper.SemesterMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.service.dto.ScheduleGenerateRequest;
import com.paike.scheduler.service.dto.ScheduleGenerateResult;
import com.paike.scheduler.service.scheduling.ScoringFunctions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V8 阶段 4 质量/性能对比基准（V8_05 T6）。
 *
 * <p>默认不参与 {@code mvn test}（耗时分钟级、需真实 MySQL）。显式触发：
 * {@code mvn -Dtest=V8BenchmarkComparisonTest -Dv8.benchmark=true test}。</p>
 *
 * <p>验收口径（经用户裁决，见 docs/v8/V8_FINAL_验收记录.md）：</p>
 * <ul>
 *   <li><b>总分线（同权重口径）</b>：SOLVER_V8 与四旧策略统一在 COMPREHENSIVE 权重下打分后比较。
 *       原因：各策略 rescore 用各自权重，跨权重直接比大小对不同策略不公；V8 与 COMPREHENSIVE
 *       共用权重，是唯一公平的横向基准。V8 自己的 rescore 总分即其 COMPREHENSIVE 权重分。</li>
 *   <li><b>未排线</b>：SOLVER_V8 未排数 ≤ 旧策略最少值（各策略口径一致，直接比）。</li>
 *   <li><b>耗时线（区分引擎/端到端）</b>：引擎求解耗时（回溯+退火，直接调 EngineFacade 打点）≤15000ms；
 *       端到端耗时（含 context 装载 + plan_item 落库 + rescore，取 performance_baseline 记录）单独记录。</li>
 * </ul>
 *
 * <p>设计：固定种子（数据/求解器），每档独立学期，先预播 COMPREHENSIVE 权重再先跑 V8，
 * 使其 EngineContext 只看到 COMPREHENSIVE SOFT 权重；tearDown 物理清理全部数据。</p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "v8.benchmark", matches = "true")
class V8BenchmarkComparisonTest {

    private static final long DATA_SEED = 20260613L;
    private static final long SOLVER_SEED = 42L;
    private static final long SOLVER_TIME_BUDGET_MS = 1_000L;
    private static final long ENGINE_TIME_GATE_MS = 15_000L;

    /** COMPREHENSIVE SOFT 权重（与 ScheduleRuleWeightService default 分支一致）。 */
    private static final BigDecimal W_CLASS_DAILY = new BigDecimal("30");
    private static final BigDecimal W_TEACHER_LOAD = new BigDecimal("30");
    private static final BigDecimal W_CONTINUOUS = new BigDecimal("25");
    private static final BigDecimal W_COURSE_DIST = new BigDecimal("25");
    private static final BigDecimal W_CLASSROOM_UTIL = new BigDecimal("20");
    private static final BigDecimal FULL_SCORE = new BigDecimal("100");

    private static final List<String> OLD_STRATEGIES = List.of(
            "TEACHER_PRIORITY", "CLASS_BALANCE", "CLASSROOM_UTILIZATION", "COMPREHENSIVE");

    @Autowired private V3ScheduleGenerateService generateService;
    @Autowired private EngineContextLoader engineContextLoader;
    @Autowired private ScheduleThresholdProperties thresholdProperties;
    @Autowired private SemesterMapper semesterMapper;
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private ClassInfoMapper classInfoMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private ClassroomMapper classroomMapper;
    @Autowired private TeachingTaskMapper teachingTaskMapper;
    @Autowired private SchedulePlanMapper planMapper;
    @Autowired private SchedulePlanItemMapper planItemMapper;
    @Autowired private ScheduleUnassignedTaskMapper unassignedTaskMapper;
    @Autowired private ScheduleRuleWeightMapper ruleWeightMapper;
    @Autowired private PerformanceBaselineRecordMapper performanceMapper;
    @Autowired private ScheduleScoreReportMapper scoreReportMapper;
    @Autowired private ScheduleScoreDetailMapper scoreDetailMapper;

    @Test
    void compareStrategiesAcrossScales() {
        List<ScaleResult> results = new ArrayList<>();
        results.add(runScale("小", 30, 10, 8, 10));
        results.add(runScale("中", 120, 35, 25, 30));
        results.add(runScale("大", 300, 80, 60, 60));

        StringBuilder table = new StringBuilder();
        table.append("\n================ V8 阶段4 质量/性能对比 ================\n");
        table.append("[同权重(COMPREHENSIVE)总分 / 未排数 / 引擎ms / 端到端ms]\n");
        table.append(String.format("%-4s %-22s %-12s %-8s %-10s %-10s %-12s%n",
                "规模", "策略", "同权重分", "未排", "引擎ms", "端到端ms", "退火步数"));
        for (ScaleResult sr : results) {
            for (StrategyRun run : sr.runs) {
                table.append(String.format("%-4s %-22s %-12s %-8s %-10s %-10s %-12s%n",
                        sr.label, run.strategyType,
                        run.sameWeightScore.toPlainString(),
                        run.unscheduledCount,
                        run.engineMs < 0 ? "-" : String.valueOf(run.engineMs),
                        run.endToEndMs,
                        run.annealingSteps < 0 ? "-" : String.valueOf(run.annealingSteps)));
            }
            table.append(String.format("%-4s 同权重: V8(%s)≥旧最高(%s) %s | 未排: V8(%d)≤旧最少(%d) %s%n",
                    sr.label,
                    sr.v8Run().sameWeightScore.toPlainString(),
                    sr.maxOldSameWeightScore().toPlainString(),
                    sr.v8ScorePasses() ? "PASS" : "FAIL",
                    sr.v8Run().unscheduledCount, sr.minOldUnscheduled(),
                    sr.v8UnscheduledPasses() ? "PASS" : "FAIL"));
        }
        ScaleResult large = results.get(2);
        boolean largeEnginePass = large.v8Run().engineMs <= ENGINE_TIME_GATE_MS;
        table.append(String.format("%-4s 引擎求解耗时≤%dms : %dms %s | 端到端: %dms (记录用)%n",
                "大", ENGINE_TIME_GATE_MS,
                large.v8Run().engineMs, largeEnginePass ? "PASS" : "FAIL",
                large.v8Run().endToEndMs));
        table.append("=======================================================\n");
        System.out.println(table);

        for (ScaleResult sr : results) {
            assertTrue(sr.v8ScorePasses(),
                    "[" + sr.label + "] 同权重下 SOLVER_V8 " + sr.v8Run().sameWeightScore
                            + " 未达旧策略最高 " + sr.maxOldSameWeightScore());
            assertTrue(sr.v8UnscheduledPasses(),
                    "[" + sr.label + "] SOLVER_V8 未排 " + sr.v8Run().unscheduledCount
                            + " 多于旧策略最少 " + sr.minOldUnscheduled());
        }
        assertTrue(largeEnginePass,
                "[大] SOLVER_V8 引擎求解耗时 " + large.v8Run().engineMs + "ms 超过 " + ENGINE_TIME_GATE_MS + "ms");
    }

    // ---------- 单档规模执行 ----------

    private ScaleResult runScale(String label, int taskCount, int teacherCount, int classCount, int roomCount) {
        String suffix = label + "_" + System.currentTimeMillis() % 1_000_000;
        Long semesterId = null;
        List<Long> teacherIds = new ArrayList<>();
        List<Long> classIds = new ArrayList<>();
        List<Long> courseIds = new ArrayList<>();
        List<Long> roomIds = new ArrayList<>();
        List<Long> taskIds = new ArrayList<>();
        List<Long> planIds = new ArrayList<>();

        try {
            Semester semester = new Semester();
            semester.setName("V8BENCH_" + suffix);
            semester.setSchoolYear("2026-2027");
            semester.setTerm("1");
            semester.setIsCurrent(0);
            semester.setStatus("ACTIVE");
            semester.setCreatedAt(LocalDateTime.now());
            semester.setUpdatedAt(LocalDateTime.now());
            semesterMapper.insert(semester);
            semesterId = semester.getId();

            seedComprehensiveWeights(semesterId);

            Random rng = new Random(DATA_SEED + (long) label.hashCode());
            teacherIds.addAll(createTeachers(teacherCount, suffix));
            classIds.addAll(createClasses(classCount, suffix));
            int courseCount = Math.max(teacherCount, taskCount / 3);
            courseIds.addAll(createCourses(courseCount, suffix));
            roomIds.addAll(createRooms(roomCount, suffix));
            taskIds.addAll(createTasks(semesterId, taskCount, teacherIds, classIds, courseIds));

            List<StrategyRun> runs = new ArrayList<>();
            runs.add(runSolverV8(semesterId, suffix, planIds));
            for (String old : OLD_STRATEGIES) {
                runs.add(runOldStrategy(old, semesterId, suffix, planIds));
            }
            return new ScaleResult(label, runs);
        } finally {
            cleanup(semesterId, planIds, taskIds, teacherIds, classIds, courseIds, roomIds);
        }
    }

    private StrategyRun runSolverV8(Long semesterId, String suffix, List<Long> planIds) {
        ScheduleGenerateRequest req = new ScheduleGenerateRequest();
        req.setSemesterId(semesterId);
        req.setStrategyType("SOLVER_V8");
        req.setPlanName("V8BENCH_SOLVER_V8_" + suffix);
        req.setOverwriteDraft(true);
        req.setSolverSeed(SOLVER_SEED);
        req.setSolverTimeBudgetMs(SOLVER_TIME_BUDGET_MS);

        ScheduleGenerateResult result = generateService.generate(req);
        planIds.add(result.getPlanId());

        long endToEndMs = readV8EndToEndMs(result.getPlanId());
        long engineMs = measureEngineSolveTime(semesterId);

        // V8 rescore 用 SOLVER_V8 权重 = COMPREHENSIVE default，故自身总分即同权重分。
        BigDecimal sameWeightScore = result.getTotalScore() == null ? BigDecimal.ZERO : result.getTotalScore();
        return new StrategyRun("SOLVER_V8", sameWeightScore, result.getUnscheduledCount(), engineMs, endToEndMs, readV8AnnealingSteps(result.getPlanId()));
    }

    private StrategyRun runOldStrategy(String strategyType, Long semesterId, String suffix, List<Long> planIds) {
        ScheduleGenerateRequest req = new ScheduleGenerateRequest();
        req.setSemesterId(semesterId);
        req.setStrategyType(strategyType);
        req.setPlanName("V8BENCH_" + strategyType + "_" + suffix);
        req.setOverwriteDraft(true);

        long start = System.nanoTime();
        ScheduleGenerateResult result = generateService.generate(req);
        long endToEndMs = (System.nanoTime() - start) / 1_000_000L;
        planIds.add(result.getPlanId());

        List<SchedulePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>().eq(SchedulePlanItem::getPlanId, result.getPlanId()));
        BigDecimal sameWeightScore = comprehensiveWeightedScore(items);

        return new StrategyRun(strategyType, sameWeightScore, result.getUnscheduledCount(), -1L, endToEndMs, -1);
    }

    private long readV8EndToEndMs(Long planId) {
        List<PerformanceBaselineRecord> records = performanceMapper.selectList(
                new LambdaQueryWrapper<PerformanceBaselineRecord>()
                        .eq(PerformanceBaselineRecord::getPlanId, planId)
                        .eq(PerformanceBaselineRecord::getOperationType,
                                PerformanceBaselineService.OP_V8_SOLVER_GENERATE));
        if (!records.isEmpty() && records.get(0).getDurationMs() != null) {
            return records.get(0).getDurationMs();
        }
        return -1L;
    }

    private int readV8AnnealingSteps(Long planId) {
        List<PerformanceBaselineRecord> records = performanceMapper.selectList(
                new LambdaQueryWrapper<PerformanceBaselineRecord>()
                        .eq(PerformanceBaselineRecord::getPlanId, planId)
                        .eq(PerformanceBaselineRecord::getOperationType,
                                PerformanceBaselineService.OP_V8_SOLVER_GENERATE));
        if (records.isEmpty() || records.get(0).getExtraJson() == null) {
            return -1;
        }
        // extraJson: {"seed":...,"timeBudgetMs":...,"optimizeTimeBudgetMs":...,"scheduledCount":...,
        //             "unassignedCount":...,"backtracks":...,"annealingSteps":12345,"initialScore":...,"finalScore":...}
        String extra = records.get(0).getExtraJson();
        int idx = extra.indexOf("\"annealingSteps\":");
        if (idx < 0) {
            return -1;
        }
        int start = idx + "\"annealingSteps\":".length();
        int end = extra.indexOf(',', start);
        if (end < 0) {
            end = extra.indexOf('}', start);
        }
        try {
            return Integer.parseInt(extra.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 直接调 EngineFacade 打点，得到纯引擎求解耗时（回溯+退火，无 DB）。 */
    private long measureEngineSolveTime(Long semesterId) {
        EngineContext ctx = engineContextLoader.load(semesterId);
        SolverConfig config = new SolverConfig(SOLVER_SEED, SolverConfig.DEFAULT_MAX_BACKTRACKS,
                SOLVER_TIME_BUDGET_MS, true);
        long start = System.nanoTime();
        EngineSolution solution = EngineFacade.solve(ctx, config);
        long engineMs = (System.nanoTime() - start) / 1_000_000L;
        // 守卫：直接求解结果应与服务内调用一致（同种子），unscheduled 应为 0。
        if (!solution.unassignedSlots().isEmpty()) {
            throw new IllegalStateException("引擎直跑出现未排 " + solution.unassignedSlots().size() + " 个，与服务调用不一致");
        }
        return engineMs;
    }

    // ---------- 同权重(COMPREHENSIVE)只读打分 ----------

    /**
     * 用 COMPREHENSIVE SOFT 权重复刻 ScheduleScoreService 的软规则打分（只读，不改库）。
     * 公式：score = 100 - Σ(weight × min(1, max(0, penalty)))，与 rescore 的 soft 分支一致。
     */
    private BigDecimal comprehensiveWeightedScore(List<SchedulePlanItem> items) {
        Map<Long, Map<Integer, Long>> classDayCounts = nestedDayCounts(items, SchedulePlanItem::getClassId);
        Map<Long, Map<Integer, Long>> teacherDayCounts = nestedDayCounts(items, SchedulePlanItem::getTeacherId);
        Map<String, Long> courseDayCounts = items.stream().collect(Collectors.groupingBy(
                item -> item.getClassId() + "_" + item.getCourseId() + "_" + item.getWeekday(),
                Collectors.counting()));
        Map<Long, Map<Integer, List<SchedulePlanItem>>> teacherDayItems = nestedDayItems(items, SchedulePlanItem::getTeacherId);
        Map<Long, Long> roomUseCounts = activeClassroomUseCounts();
        for (SchedulePlanItem item : items) {
            if (item.getClassroomId() != null) {
                roomUseCounts.merge(item.getClassroomId(), 1L, Long::sum);
            }
        }
        int afternoonStart = thresholdProperties.getAfternoonStartPeriod();

        BigDecimal penalty = BigDecimal.ZERO;
        penalty = penalty.add(W_CLASS_DAILY.multiply(minOne(ScoringFunctions.penaltyVariance(classDayCounts))));
        penalty = penalty.add(W_TEACHER_LOAD.multiply(minOne(ScoringFunctions.penaltyVariance(teacherDayCounts))));
        penalty = penalty.add(W_COURSE_DIST.multiply(minOne(ScoringFunctions.penaltyDuplicateCourse(courseDayCounts))));
        penalty = penalty.add(W_CONTINUOUS.multiply(minOne(ScoringFunctions.penaltyContinuous(teacherDayItems))));
        penalty = penalty.add(W_CLASSROOM_UTIL.multiply(minOne(
                ScoringFunctions.penaltyClassroomUtilization(roomUseCounts, items.size()))));
        return FULL_SCORE.subtract(penalty).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal minOne(BigDecimal v) {
        BigDecimal n = v == null ? BigDecimal.ZERO : v.max(BigDecimal.ZERO);
        return n.min(BigDecimal.ONE);
    }

    private Map<Long, Map<Integer, Long>> nestedDayCounts(List<SchedulePlanItem> items,
                                                          Function<SchedulePlanItem, Long> ownerFunc) {
        return items.stream().collect(Collectors.groupingBy(
                ownerFunc,
                Collectors.groupingBy(SchedulePlanItem::getWeekday, Collectors.counting())));
    }

    private Map<Long, Map<Integer, List<SchedulePlanItem>>> nestedDayItems(List<SchedulePlanItem> items,
                                                                           Function<SchedulePlanItem, Long> ownerFunc) {
        return items.stream().collect(Collectors.groupingBy(
                ownerFunc,
                Collectors.groupingBy(SchedulePlanItem::getWeekday)));
    }

    private Map<Long, Long> activeClassroomUseCounts() {
        List<Classroom> classrooms = classroomMapper.selectList(
                new LambdaQueryWrapper<Classroom>().eq(Classroom::getStatus, 1));
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Classroom c : classrooms) {
            counts.put(c.getId(), 0L);
        }
        return counts;
    }

    // ---------- 合成数据生成（固定种子） ----------

    private List<Long> createTeachers(int count, String suffix) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Teacher t = new Teacher();
            t.setTeacherNo("BENCH_" + suffix + "_T" + i);
            t.setName("基准教师" + suffix + "_" + i);
            t.setDepartment("基准系");
            t.setStatus(1);
            t.setDeleted(0);
            teacherMapper.insert(t);
            ids.add(t.getId());
        }
        return ids;
    }

    private List<Long> createClasses(int count, String suffix) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ClassInfo c = new ClassInfo();
            c.setClassName("基准班级" + suffix + "_" + i);
            c.setMajor("基准专业");
            c.setGrade("2026");
            c.setStudentCount(40);
            c.setStatus(1);
            c.setDeleted(0);
            classInfoMapper.insert(c);
            ids.add(c.getId());
        }
        return ids;
    }

    private List<Long> createCourses(int count, String suffix) {
        List<Long> ids = new ArrayList<>();
        String[] types = {"NORMAL", "EXPERIMENT", "COMPUTER"};
        for (int i = 0; i < count; i++) {
            Course c = new Course();
            c.setCourseNo("BENCH_" + suffix + "_C" + i);
            c.setCourseName("基准课程" + suffix + "_" + i);
            c.setCourseType(types[i % 3]);
            c.setWeeklyHours(2);
            c.setDeleted(0);
            courseMapper.insert(c);
            ids.add(c.getId());
        }
        return ids;
    }

    private List<Long> createRooms(int count, String suffix) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Classroom r = new Classroom();
            String type;
            int cap;
            int mod = i % 4;
            if (mod == 0) { type = "NORMAL"; cap = 60; }
            else if (mod == 1) { type = "LAB"; cap = 60; }
            else if (mod == 2) { type = "COMPUTER"; cap = 60; }
            else { type = "NORMAL"; cap = 80; }
            r.setRoomName("BENCH_" + suffix + "_R" + i);
            r.setBuilding("基准楼");
            r.setCapacity(cap);
            r.setRoomType(type);
            r.setStatus(1);
            r.setDeleted(0);
            classroomMapper.insert(r);
            ids.add(r.getId());
        }
        return ids;
    }

    private List<Long> createTasks(Long semesterId, int count, List<Long> teacherIds,
                                   List<Long> classIds, List<Long> courseIds) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TeachingTask t = new TeachingTask();
            t.setSemesterId(semesterId);
            t.setTeacherId(teacherIds.get(i % teacherIds.size()));
            t.setClassId(classIds.get(i % classIds.size()));
            t.setCourseId(courseIds.get(i % courseIds.size()));
            t.setWeeklyHours(2);
            t.setStatus(1);
            t.setDeleted(0);
            teachingTaskMapper.insert(t);
            ids.add(t.getId());
        }
        return ids;
    }

    private void seedComprehensiveWeights(Long semesterId) {
        String st = "COMPREHENSIVE";
        Object[][] rules = {
                {"TEACHER_TIME_CONFLICT", "教师时间冲突", "HARD", "100"},
                {"CLASS_TIME_CONFLICT", "班级时间冲突", "HARD", "100"},
                {"CLASSROOM_TIME_CONFLICT", "教室时间冲突", "HARD", "100"},
                {"TEACHER_UNAVAILABLE", "教师禁排时间", "HARD", "90"},
                {"CLASSROOM_CAPACITY", "教室容量不足", "HARD", "80"},
                {"CLASSROOM_TYPE_MISMATCH", "教室类型不匹配", "HARD", "80"},
                {"CLASS_DAILY_BALANCE", "班级每日均衡", "SOFT", "30"},
                {"TEACHER_DAILY_LOAD", "教师每日负载", "SOFT", "30"},
                {"CONTINUOUS_PERIOD_LIMIT", "连续上课限制", "SOFT", "25"},
                {"COURSE_DISTRIBUTION", "课程分布均衡", "SOFT", "25"},
                {"CLASSROOM_UTILIZATION", "教室利用率", "SOFT", "20"},
        };
        for (Object[] r : rules) {
            ScheduleRuleWeight w = new ScheduleRuleWeight();
            w.setSemesterId(semesterId);
            w.setStrategyType(st);
            w.setRuleCode((String) r[0]);
            w.setRuleName((String) r[1]);
            w.setRuleType((String) r[2]);
            w.setWeight(new BigDecimal((String) r[3]));
            w.setEnabled(1);
            w.setDescription((String) r[1]);
            ruleWeightMapper.insert(w);
        }
    }

    // ---------- 清理 ----------

    private void cleanup(Long semesterId, List<Long> planIds, List<Long> taskIds,
                         List<Long> teacherIds, List<Long> classIds,
                         List<Long> courseIds, List<Long> roomIds) {
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
            ruleWeightMapper.delete(new LambdaQueryWrapper<ScheduleRuleWeight>()
                    .eq(ScheduleRuleWeight::getSemesterId, semesterId));
        }
        for (Long id : taskIds) {
            teachingTaskMapper.deleteById(id);
        }
        for (Long id : roomIds) {
            classroomMapper.deleteById(id);
        }
        for (Long id : courseIds) {
            courseMapper.deleteById(id);
        }
        for (Long id : classIds) {
            classInfoMapper.deleteById(id);
        }
        for (Long id : teacherIds) {
            teacherMapper.deleteById(id);
        }
        if (semesterId != null) {
            semesterMapper.deleteById(semesterId);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }

    private record StrategyRun(String strategyType, BigDecimal sameWeightScore,
                               int unscheduledCount, long engineMs, long endToEndMs,
                               int annealingSteps) {}

    private record ScaleResult(String label, List<StrategyRun> runs) {
        StrategyRun v8Run() {
            return runs.stream().filter(r -> "SOLVER_V8".equals(r.strategyType)).findFirst().orElseThrow();
        }

        BigDecimal maxOldSameWeightScore() {
            return runs.stream().filter(r -> !"SOLVER_V8".equals(r.strategyType))
                    .map(StrategyRun::sameWeightScore).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        }

        int minOldUnscheduled() {
            return runs.stream().filter(r -> !"SOLVER_V8".equals(r.strategyType))
                    .mapToInt(StrategyRun::unscheduledCount).min().orElse(0);
        }

        boolean v8ScorePasses() {
            return v8Run().sameWeightScore.compareTo(maxOldSameWeightScore()) >= 0;
        }

        boolean v8UnscheduledPasses() {
            return v8Run().unscheduledCount <= minOldUnscheduled();
        }
    }
}
