package com.paike.scheduler.service;

import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleScoreDetailMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归基线锁定测试：用最小 fixture（5-3 条 SchedulePlanItem）固定每条规则的扣分数值。
 *
 * <p>这不是单纯的算法单测——目的是<b>未来公式动一眼能看出来</b>：
 * 任何 penalty/score 公式被无意改动，都会让锁定的具体数值断言失败。
 * 数值在测试代码里手算注释好，便于代码审查反查。
 */
class ScheduleScoreServiceTest {

    private ScheduleScoreDetailMapper scoreDetailMapper;
    private SchedulePlanMapper planMapper;
    private SchedulePlanItemMapper planItemMapper;
    private ClassroomMapper classroomMapper;
    private ScheduleRuleWeightService ruleWeightService;
    private ScheduleThresholdProperties thresholds;
    private ScheduleScoreService service;

    @BeforeEach
    void setUp() {
        scoreDetailMapper = mock(ScheduleScoreDetailMapper.class);
        planMapper = mock(SchedulePlanMapper.class);
        planItemMapper = mock(SchedulePlanItemMapper.class);
        classroomMapper = mock(ClassroomMapper.class);
        ruleWeightService = mock(ScheduleRuleWeightService.class);
        thresholds = mock(ScheduleThresholdProperties.class);
        when(thresholds.getAfternoonStartPeriod()).thenReturn(5);
        when(classroomMapper.selectList(any())).thenReturn(List.of());
        service = new ScheduleScoreService(
                scoreDetailMapper, planMapper, planItemMapper, classroomMapper, ruleWeightService, thresholds);
    }

    /**
     * 5 软规则 fixture：5 条 item 跨 2 班 2 教师 2 已用教室 2 天，另有 1 间启用教室未使用。
     * 触发软罚分但零硬冲突。
     *
     * <pre>
     * items:
     *   1) t1 c1 cs1 r1 day1 p1
     *   2) t1 c1 cs1 r1 day1 p3   (同班同课同日，连续 1-3)
     *   3) t2 c2 cs2 r2 day2 p5   (下午)
     *   4) t2 c2 cs2 r2 day2 p7   (同班同课同日，连续 5-7，下午)
     *   5) t1 c1 cs1 r2 day2 p1
     *
     * 罚分逐项手算（公式见 ScoringFunctions）：
     *   variancePenalty(class day counts)
     *     c1: {d1:2, d2:1} → avg=1.5, var=0.25, contrib=min(1,0.25/4)=0.0625
     *     c2: {d2:2}        → size<=1 skip
     *     normalize: 0.0625 / 2 owners = 0.03125 → scale(4)=0.0313
     *     soft: weight 30 × 0.0313 = 0.939 → -0.94；level=3
     *   variancePenalty(teacher day counts)：同形状 → -0.94，level=3
     *   duplicateCoursePenalty:
     *     courseDayCounts: c1_cs1_d1=2(*), c1_cs1_d2=1, c2_cs2_d2=2(*) → dup=2/total=3=0.6667
     *     soft: weight 25 × 0.6667 = 16.6675 → -16.67；level=67
     *   continuousPenalty:
     *     t1_d1 starts=[1,3] → chains=1 → 0.5
     *     t1_d2 starts=[1]   → 0
     *     t2_d2 starts=[5,7] → chains=1 → 0.5
     *     sum=1.0 / sample 3 = 0.3333 → -8.33；level=33
     *   classroomUtilizationPenalty:
     *     active rooms: r1=2, r2=3, r3=0, avg=5/3, var=14/9
     *     min(1, (14/9)/(25/9)) = 0.56 → -11.20；level=56
     *
     * 总分 = 100 + (-0.94 -0.94 -16.67 -8.33 -11.20) = 61.92
     * </pre>
     */
    @Test
    void rescore_lockedBaseline_5softRulesNoConflict() {
        SchedulePlan plan = newPlan(1L);

        when(planItemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, 1L, 1L, 1, 1),
                item(1L, 1L, 1L, 1L, 1, 3),
                item(2L, 2L, 2L, 2L, 2, 5),
                item(2L, 2L, 2L, 2L, 2, 7),
                item(1L, 1L, 1L, 2L, 2, 1)));
        when(classroomMapper.selectList(any())).thenReturn(List.of(
                classroom(1L),
                classroom(2L),
                classroom(3L)));

        when(ruleWeightService.list(2L, "COMPREHENSIVE", null)).thenReturn(List.of(
                rule("CLASS_DAILY_BALANCE", "SOFT", "30"),
                rule("TEACHER_DAILY_LOAD", "SOFT", "30"),
                rule("COURSE_DISTRIBUTION", "SOFT", "25"),
                rule("CONTINUOUS_PERIOD_LIMIT", "SOFT", "25"),
                rule("CLASSROOM_UTILIZATION", "SOFT", "20")));

        service.rescore(plan);

        Map<String, ScheduleScoreDetail> byCode = captureDetailsByCode(5);

        assertDetail(byCode, "CLASS_DAILY_BALANCE", "-0.94",
                "班级每日均衡偏差 3%，扣 0.94 分（满分 30）");
        assertDetail(byCode, "TEACHER_DAILY_LOAD", "-0.94",
                "教师每日负载偏差 3%，扣 0.94 分（满分 30）");
        assertDetail(byCode, "COURSE_DISTRIBUTION", "-16.67",
                "课程分布均衡偏差 67%，扣 16.67 分（满分 25）");
        assertDetail(byCode, "CONTINUOUS_PERIOD_LIMIT", "-8.33",
                "连续上课限制偏差 33%，扣 8.33 分（满分 25）");
        assertDetail(byCode, "CLASSROOM_UTILIZATION", "-11.20",
                "教室利用率偏差 56%，扣 11.20 分（满分 20）");

        assertEquals(new BigDecimal("61.92"), plan.getTotalScore());
        assertEquals(0, plan.getConflictCount());
    }

    /**
     * 3 硬规则 fixture：3 条 item 同 (teacher/class/room, weekday, startPeriod) → 三维同时冲突。
     *
     * <pre>
     * 同一个 slot 有 3 条 → conflict count = size - 1 = 2 (每维都 2)
     * hardMetric: weight 100 × 2 = -200.00
     * 总分 = 100 + (-200 × 3) = -500 → clamp 到 0.00
     * conflictCount = 2(teacher) + 2(class) + 2(room) = 6
     * </pre>
     */
    @Test
    void rescore_lockedBaseline_hardConflictsClampedToZero() {
        SchedulePlan plan = newPlan(2L);

        when(planItemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, 1L, 1L, 1, 1),
                item(1L, 1L, 1L, 1L, 1, 1),
                item(1L, 1L, 1L, 1L, 1, 1)));

        when(ruleWeightService.list(2L, "COMPREHENSIVE", null)).thenReturn(List.of(
                rule("TEACHER_TIME_CONFLICT", "HARD", "100"),
                rule("CLASS_TIME_CONFLICT", "HARD", "100"),
                rule("CLASSROOM_TIME_CONFLICT", "HARD", "100")));

        service.rescore(plan);

        Map<String, ScheduleScoreDetail> byCode = captureDetailsByCode(3);

        assertDetail(byCode, "TEACHER_TIME_CONFLICT", "-200.00",
                "教师时间冲突违规 2 次，扣 200.00 分（满分 100）");
        assertDetail(byCode, "CLASS_TIME_CONFLICT", "-200.00",
                "班级时间冲突违规 2 次，扣 200.00 分（满分 100）");
        assertDetail(byCode, "CLASSROOM_TIME_CONFLICT", "-200.00",
                "教室时间冲突违规 2 次，扣 200.00 分（满分 100）");

        assertEquals(new BigDecimal("0.00"), plan.getTotalScore());
        assertEquals(6, plan.getConflictCount());
    }

    /**
     * 空 plan：零 item，所有规则要么"无违规"要么"表现良好"，总分 100.00。
     */
    @Test
    void rescore_emptyPlan_keepsFullScore() {
        SchedulePlan plan = newPlan(3L);

        when(planItemMapper.selectList(any())).thenReturn(List.of());
        when(ruleWeightService.list(2L, "COMPREHENSIVE", null)).thenReturn(List.of(
                rule("TEACHER_TIME_CONFLICT", "HARD", "100"),
                rule("CLASS_DAILY_BALANCE", "SOFT", "30")));

        service.rescore(plan);

        Map<String, ScheduleScoreDetail> byCode = captureDetailsByCode(2);

        assertDetail(byCode, "TEACHER_TIME_CONFLICT", "0.00",
                "教师时间冲突无违规（满分 100）");
        assertDetail(byCode, "CLASS_DAILY_BALANCE", "0.00",
                "班级每日均衡表现良好（满分 30）");

        assertEquals(new BigDecimal("100.00"), plan.getTotalScore());
        assertEquals(0, plan.getConflictCount());
    }

    /**
     * MORNING_THEORY_PRIORITY fixture：4 条 item（2 上午 startPeriod=1/3，2 下午 startPeriod=5/7），
     * 跨不同 teacher/class/room/weekday 避免触发其他冲突维度。
     *
     * <pre>
     * penaltyMorningPriority:
     *   afternoonStartPeriod=5（@BeforeEach mock）
     *   afternoonCount = 2 (items with sp=5,7)
     *   total = 4
     *   ratio = 2/4 = 0.5 → setScale(4)=0.5000
     * soft: weight 20 × 0.5 = 10.0 → -10.00；level=50
     * 总分 = 100 - 10 = 90.00
     * </pre>
     *
     * 这条 fixture 专门 lock {@link com.paike.scheduler.service.scheduling.ScoringFunctions#penaltyMorningPriority}
     * 的搬运等价性（D2 C2 集中后该路径在原 3 个 fixture 里没被覆盖到）。
     */
    @Test
    void rescore_lockedBaseline_morningPriorityRule() {
        SchedulePlan plan = newPlan(4L);

        when(planItemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, 1L, 1L, 1, 1),
                item(2L, 2L, 2L, 2L, 2, 3),
                item(3L, 3L, 3L, 3L, 3, 5),
                item(4L, 4L, 4L, 4L, 4, 7)));

        when(ruleWeightService.list(2L, "COMPREHENSIVE", null)).thenReturn(List.of(
                rule("MORNING_THEORY_PRIORITY", "SOFT", "20")));

        service.rescore(plan);

        Map<String, ScheduleScoreDetail> byCode = captureDetailsByCode(1);

        assertDetail(byCode, "MORNING_THEORY_PRIORITY", "-10.00",
                "理论课优先上午偏差 50%，扣 10.00 分（满分 20）");

        assertEquals(new BigDecimal("90.00"), plan.getTotalScore());
        assertEquals(0, plan.getConflictCount());
    }

    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------

    private SchedulePlan newPlan(long id) {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(id);
        plan.setSemesterId(2L);
        plan.setStrategyType("COMPREHENSIVE");
        plan.setUnscheduledCount(0);
        return plan;
    }

    private SchedulePlanItem item(Long teacherId, Long classId, Long courseId, Long classroomId,
                                  int weekday, int startPeriod) {
        SchedulePlanItem it = new SchedulePlanItem();
        it.setTeacherId(teacherId);
        it.setClassId(classId);
        it.setCourseId(courseId);
        it.setClassroomId(classroomId);
        it.setWeekday(weekday);
        it.setStartPeriod(startPeriod);
        return it;
    }

    private Classroom classroom(long id) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setStatus(1);
        classroom.setDeleted(0);
        return classroom;
    }

    private ScheduleRuleWeight rule(String code, String type, String weight) {
        ScheduleRuleWeight r = new ScheduleRuleWeight();
        r.setRuleCode(code);
        r.setRuleType(type);
        r.setRuleName(code);
        r.setWeight(new BigDecimal(weight));
        r.setEnabled(1);
        return r;
    }

    private Map<String, ScheduleScoreDetail> captureDetailsByCode(int expectedCount) {
        ArgumentCaptor<ScheduleScoreDetail> captor = ArgumentCaptor.forClass(ScheduleScoreDetail.class);
        verify(scoreDetailMapper, times(expectedCount)).insert(captor.capture());
        return captor.getAllValues().stream()
                .collect(Collectors.toMap(ScheduleScoreDetail::getRuleCode, d -> d));
    }

    private void assertDetail(Map<String, ScheduleScoreDetail> byCode,
                              String code, String expectedScore, String expectedMessage) {
        ScheduleScoreDetail d = byCode.get(code);
        assertEquals(new BigDecimal(expectedScore), d.getScore(), "score of " + code);
        assertEquals(expectedMessage, d.getDetailMessage(), "detailMessage of " + code);
    }
}
