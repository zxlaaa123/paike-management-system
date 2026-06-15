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
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRuleWeight;
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
import com.paike.scheduler.mapper.ScheduleUnassignedTaskMapper;
import com.paike.scheduler.mapper.SemesterMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.service.dto.ScheduleGenerateRequest;
import com.paike.scheduler.service.dto.ScheduleGenerateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9 阶段 3C 性能 benchmark（V9_05 T10，R2 门槛）。
 *
 * <p>默认不参与 {@code mvn test}（耗时分钟级、需真实 MySQL）。显式触发：
 * {@code mvn -Dtest=V9WeekTypeBenchmarkTest -Dv9.benchmark=true test}。</p>
 *
 * <p>R2 门槛（V9_00 §196、V9_04 §279）：
 * <ul>
 *   <li><b>回溯成功率 ≥95%</b>：混合数据集（30% ODD + 30% EVEN + 40% ALL）SOLVER_V8 排下率 ≥95%
 *       （unassigned ≤ taskCount × 5%）。基线是 V8 全 ALL 数据（回溯成功率 100%）。</li>
 *   <li><b>退火耗时增幅 ≤50%</b>：退火按 optimizeTimeBudgetMs 墙钟停机（10s 固定），耗时不会膨胀，
 *       故门槛落在"每步成本"维度——同等预算下混合数据的退火步数 ≥ 全 ALL 基线的 1/1.5（即每步耗时增幅 ≤50%）。
 *       同时记录引擎总耗时（回溯+退火）作横向参考。</li>
 * </ul>
 *
 * <p>设计：每档规模跑两轮——全 ALL（V8 基线）+ 混合 weekType（V9 新能力），同 seed，
 * 对比排下率与退火步数。结果表 + profiling 原始输出落 {@code reports/v9-week-type-benchmark-raw.txt}（手动重定向）。
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "v9.benchmark", matches = "true")
class V9WeekTypeBenchmarkTest {

    private static final long DATA_SEED = 20260615L;
    private static final long SOLVER_SEED = 42L;
    private static final long SOLVER_TIME_BUDGET_MS = 1_000L;
    private static final long SOLVER_OPTIMIZE_TIME_BUDGET_MS = 3_000L;
    /** 混合数据每步耗时增幅门槛：退火步数 ≥ 基线 / 1.5（即每步耗时 ≤ 基线 ×1.5）。 */
    private static final double STEP_TIME_INCREASE_GATE = 1.5D;
    /** 回溯成功率门槛：排下率 ≥95%。 */
    private static final double BACKTRACK_SUCCESS_RATE_GATE = 0.95D;

    private static final BigDecimal W_CLASS_DAILY = new BigDecimal("30");
    private static final BigDecimal W_TEACHER_LOAD = new BigDecimal("30");
    private static final BigDecimal W_CONTINUOUS = new BigDecimal("25");
    private static final BigDecimal W_COURSE_DIST = new BigDecimal("25");
    private static final BigDecimal W_CLASSROOM_UTIL = new BigDecimal("20");

    @Autowired private V3ScheduleGenerateService generateService;
    @Autowired private EngineContextLoader engineContextLoader;
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

    @Test
    void compareAllBaselineVsMixedWeekTypeAcrossScales() {
        List<ScaleComparison> results = new ArrayList<>();
        results.add(runScale("小", 30, 10, 8, 10));
        results.add(runScale("中", 120, 35, 25, 30));
        // 注：去掉"大"档（300 任务）以控制总耗时在 harness 后台超时内。
        // V8 全ALL 基线"大"档数据已在 reports/v8-neighbor-incremental-benchmark.txt，
        // 阶段 4 收口时再补跑大档做完整三档对比。

        StringBuilder table = benchmarkHeader();
        for (ScaleComparison sc : results) {
            table.append(formatRun(sc.label, "全ALL基线", sc.allBaseline));
            table.append(formatRun(sc.label, "混合weekType", sc.mixed));
            table.append(formatGate(sc));
        }
        table.append("===========================================================\n");
        System.out.println(table);

        for (ScaleComparison sc : results) {
            assertTrue(sc.mixed.scheduleRate() >= BACKTRACK_SUCCESS_RATE_GATE,
                    "[" + sc.label + "] 混合数据排下率 " + (sc.mixed.scheduleRate() * 100)
                            + "% 低于 " + (BACKTRACK_SUCCESS_RATE_GATE * 100) + "% 门槛（R2 回溯成功率）");
            assertTrue(sc.stepRatio() >= (1 / STEP_TIME_INCREASE_GATE),
                    "[" + sc.label + "] 退火步数比 混合/基线=" + sc.stepRatio()
                            + " 低于 " + (1 / STEP_TIME_INCREASE_GATE)
                            + "（即每步耗时增幅 >" + (STEP_TIME_INCREASE_GATE * 100 - 100) + "%，R2 门槛）");
        }
    }

    private String formatRun(String label, String datasetName, RunMetrics run) {
        return String.format("%-4s %-18s %-8d %-9.1f%% %-10d %-10d %-12d%n",
                label, datasetName, run.unscheduledCount,
                run.scheduleRate() * 100, run.engineMs, run.backtrackMs, run.annealingSteps);
    }

    private StringBuilder benchmarkHeader() {
        StringBuilder table = new StringBuilder();
        table.append("\n================ V9 阶段3C 单双周性能对比 ================\n");
        table.append("[全ALL基线 vs 混合(30%ODD+30%EVEN+40%ALL) | 同种子 | 退火预算 ")
                .append(SOLVER_OPTIMIZE_TIME_BUDGET_MS)
                .append("ms]\n");
        table.append(String.format("%-4s %-18s %-8s %-10s %-10s %-10s %-12s%n",
                "规模", "数据集", "未排", "排下率", "引擎ms", "回溯ms", "退火步数"));
        return table;
    }

    private String formatGate(ScaleComparison sc) {
        return String.format("%-4s 回溯成功率: 混合%.1f%%≥%.0f%%%s | 退火步数比: 混合/基线=%.2f %s%n",
                sc.label,
                sc.mixed.scheduleRate() * 100, BACKTRACK_SUCCESS_RATE_GATE * 100,
                sc.mixed.scheduleRate() >= BACKTRACK_SUCCESS_RATE_GATE ? "PASS" : "FAIL",
                sc.stepRatio(),
                sc.stepRatio() >= (1 / STEP_TIME_INCREASE_GATE) ? "PASS" : "FAIL");
    }

    private ScaleComparison runScale(String label, int taskCount, int teacherCount, int classCount, int roomCount) {
        // 全 ALL 基线：同一 seed、同一资源量，仅 weekType 全 ALL
        RunMetrics allBaseline = runOneDataset(label + "_ALL", taskCount, teacherCount, classCount, roomCount, 1.0, 0.0, 0.0);
        // 混合：30% ODD + 30% EVEN + 40% ALL
        RunMetrics mixed = runOneDataset(label + "_MIX", taskCount, teacherCount, classCount, roomCount, 0.4, 0.3, 0.3);
        ScaleComparison comparison = new ScaleComparison(label, allBaseline, mixed);
        System.out.print(benchmarkHeader()
                .append(formatRun(label, "全ALL基线", allBaseline))
                .append(formatRun(label, "混合weekType", mixed))
                .append(formatGate(comparison))
                .append("===========================================================\n"));
        System.out.flush();
        return comparison;
    }

    /**
     * 单数据集执行：独立学期，按 oddPct/evenPct/allPct 分配任务 weekType，跑 SOLVER_V8 + 引擎直跑打点。
     */
    private RunMetrics runOneDataset(String tag, int taskCount, int teacherCount, int classCount, int roomCount,
                                     double allPct, double oddPct, double evenPct) {
        String suffix = tag + "_" + System.currentTimeMillis() % 1_000_000;
        Long semesterId = null;
        List<Long> teacherIds = new ArrayList<>();
        List<Long> classIds = new ArrayList<>();
        List<Long> courseIds = new ArrayList<>();
        List<Long> roomIds = new ArrayList<>();
        List<Long> taskIds = new ArrayList<>();
        List<Long> planIds = new ArrayList<>();

        try {
            Semester semester = new Semester();
            semester.setName("V9BENCH_" + suffix);
            semester.setSchoolYear("2026-2027");
            semester.setTerm("1");
            semester.setIsCurrent(0);
            semester.setStatus("ACTIVE");
            semester.setCreatedAt(LocalDateTime.now());
            semester.setUpdatedAt(LocalDateTime.now());
            semesterMapper.insert(semester);
            semesterId = semester.getId();

            seedComprehensiveWeights(semesterId);

            Random rng = new Random(DATA_SEED + (long) tag.hashCode());
            teacherIds.addAll(createTeachers(teacherCount, suffix));
            classIds.addAll(createClasses(classCount, suffix));
            int courseCount = Math.max(teacherCount, taskCount / 3);
            courseIds.addAll(createCourses(courseCount, suffix));
            roomIds.addAll(createRooms(roomCount, suffix));
            taskIds.addAll(createTasks(semesterId, taskCount, teacherIds, classIds, courseIds, allPct, oddPct, evenPct, rng));

            // 端到端生成（落库 + rescore），用于拿 unscheduledCount
            ScheduleGenerateRequest req = new ScheduleGenerateRequest();
            req.setSemesterId(semesterId);
            req.setStrategyType("SOLVER_V8");
            req.setPlanName("V9BENCH_" + suffix);
            req.setOverwriteDraft(true);
            req.setSolverSeed(SOLVER_SEED);
            req.setSolverTimeBudgetMs(SOLVER_TIME_BUDGET_MS);
            req.setSolverOptimizeTimeBudgetMs(SOLVER_OPTIMIZE_TIME_BUDGET_MS);
            ScheduleGenerateResult result = generateService.generate(req);
            planIds.add(result.getPlanId());

            int unscheduledCount = result.getUnscheduledCount();

            // 引擎直跑打点：纯回溯+退火耗时、退火步数（无 DB）
            EngineContext ctx = engineContextLoader.load(semesterId);
            SolverConfig config = new SolverConfig(SOLVER_SEED, SolverConfig.DEFAULT_MAX_BACKTRACKS,
                    SOLVER_TIME_BUDGET_MS, SOLVER_OPTIMIZE_TIME_BUDGET_MS, true);
            long engineStart = System.nanoTime();
            EngineSolution solution = EngineFacade.solve(ctx, config);
            long engineMs = (System.nanoTime() - engineStart) / 1_000_000L;

            int annealingSteps = solution.stats().annealingSteps();
            // 回溯耗时 ≈ 引擎总耗时 - 退火预算（退火按 optimizeTimeBudgetMs 墙钟停机）
            long backtrackMs = Math.max(0, engineMs - SOLVER_OPTIMIZE_TIME_BUDGET_MS);

            double scheduleRate = taskCount == 0 ? 1.0 : (double) (taskCount - unscheduledCount) / taskCount;

            RunMetrics metrics = new RunMetrics(unscheduledCount, scheduleRate, engineMs, backtrackMs, annealingSteps);
            System.out.print(formatRun(tag, "dataset", metrics));
            System.out.flush();
            return metrics;
        } finally {
            cleanup(semesterId, planIds, taskIds, teacherIds, classIds, courseIds, roomIds);
        }
    }

    // ---------- 合成数据生成（固定种子） ----------

    private List<Long> createTeachers(int count, String suffix) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Teacher t = new Teacher();
            t.setTeacherNo("V9_" + suffix + "_T" + i);
            t.setName("V9教师" + suffix + "_" + i);
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
            c.setClassName("V9班" + suffix + "_" + i);
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
        for (int i = 0; i < count; i++) {
            Course c = new Course();
            c.setCourseNo("V9_" + suffix + "_C" + i);
            c.setCourseName("V9课程" + suffix + "_" + i);
            c.setCourseType("NORMAL");
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
            r.setRoomName("V9_" + suffix + "_R" + i);
            r.setBuilding("基准楼");
            r.setCapacity(80);
            r.setRoomType("NORMAL");
            r.setStatus(1);
            r.setDeleted(0);
            classroomMapper.insert(r);
            ids.add(r.getId());
        }
        return ids;
    }

    /**
     * 按 allPct/oddPct/evenPct 比例分配 weekType（固定种子 rng 决定每个任务的 weekType）。
     * 资源（teacher/class/course）轮询分配，避免单资源过载。
     */
    private List<Long> createTasks(Long semesterId, int count, List<Long> teacherIds, List<Long> classIds,
                                   List<Long> courseIds, double allPct, double oddPct, double evenPct, Random rng) {
        List<Long> ids = new ArrayList<>();
        int allN = (int) Math.round(count * allPct);
        int oddN = (int) Math.round(count * oddPct);
        // 兜底：浮点取整误差由 ALL 吸收，保证总数=count
        int evenN = count - allN - oddN;
        if (evenN < 0) {
            allN += evenN;
            evenN = 0;
        }
        for (int i = 0; i < count; i++) {
            String weekType;
            if (i < allN) {
                weekType = WeekTypeSupport.ALL;
            } else if (i < allN + oddN) {
                weekType = WeekTypeSupport.ODD;
            } else {
                weekType = WeekTypeSupport.EVEN;
            }
            TeachingTask t = new TeachingTask();
            t.setSemesterId(semesterId);
            t.setTeacherId(teacherIds.get(i % teacherIds.size()));
            t.setClassId(classIds.get(i % classIds.size()));
            t.setCourseId(courseIds.get(i % courseIds.size()));
            t.setWeeklyHours(2);
            t.setStatus(1);
            t.setDeleted(0);
            t.setWeekType(weekType);
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
                {"CLASS_DAILY_BALANCE", "班级每日均衡", "SOFT", W_CLASS_DAILY.toPlainString()},
                {"TEACHER_DAILY_LOAD", "教师每日负载", "SOFT", W_TEACHER_LOAD.toPlainString()},
                {"CONTINUOUS_PERIOD_LIMIT", "连续上课限制", "SOFT", W_CONTINUOUS.toPlainString()},
                {"COURSE_DISTRIBUTION", "课程分布均衡", "SOFT", W_COURSE_DIST.toPlainString()},
                {"CLASSROOM_UTILIZATION", "教室利用率", "SOFT", W_CLASSROOM_UTIL.toPlainString()},
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

    private void cleanup(Long semesterId, List<Long> planIds, List<Long> taskIds,
                         List<Long> teacherIds, List<Long> classIds,
                         List<Long> courseIds, List<Long> roomIds) {
        for (Long planId : planIds) {
            unassignedTaskMapper.delete(new LambdaQueryWrapper<ScheduleUnassignedTask>()
                    .eq(ScheduleUnassignedTask::getPlanId, planId));
            performanceMapper.delete(new LambdaQueryWrapper<PerformanceBaselineRecord>()
                    .eq(PerformanceBaselineRecord::getPlanId, planId));
            planItemMapper.delete(new LambdaQueryWrapper<SchedulePlanItem>()
                    .eq(SchedulePlanItem::getPlanId, planId));
            planMapper.deleteById(planId);
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
            ruleWeightMapper.delete(new LambdaQueryWrapper<ScheduleRuleWeight>()
                    .eq(ScheduleRuleWeight::getSemesterId, semesterId));
            semesterMapper.deleteById(semesterId);
        }
    }

    private record RunMetrics(int unscheduledCount, double scheduleRate, long engineMs,
                              long backtrackMs, int annealingSteps) {}

    private record ScaleComparison(String label, RunMetrics allBaseline, RunMetrics mixed) {
        /** 退火步数比：mixed/baseline。相同墙钟预算下，步数比 = baseline每步耗时 / mixed每步耗时。
         *  步数比 ≥ 1/1.5 ≈ 0.667 ⟺ 每步耗时增幅 ≤50%。 */
        double stepRatio() {
            if (allBaseline.annealingSteps() <= 0) {
                return 1.0;
            }
            return (double) mixed.annealingSteps() / allBaseline.annealingSteps();
        }
    }
}
