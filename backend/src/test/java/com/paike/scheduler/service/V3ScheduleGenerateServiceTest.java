package com.paike.scheduler.service;

import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.service.dto.ScheduleGenerateRequest;
import com.paike.scheduler.service.dto.ScheduleGenerateResult;
import com.paike.scheduler.service.scheduling.SchedulingReferenceLoader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class V3ScheduleGenerateServiceTest {

    @Test
    void candidateScoringUsesDeltaPenaltyWithoutDeadLegacyBranch() throws IOException {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "V3ScheduleGenerateService.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("USE_DELTA_PENALTY_SCORING"));
        assertFalse(source.contains("scoreCandidateLegacy"));
        assertTrue(source.contains("DeltaPenaltyScorer.weightedSoftDeltaPenalty"));
    }

    @Test
    void solverV8GeneratesPlanItemsThroughEngineContext() {
        SemesterService semesterService = mock(SemesterService.class);
        ScheduleRuleService ruleService = mock(ScheduleRuleService.class);
        ScheduleScoreService scoreService = mock(ScheduleScoreService.class);
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        SchedulingReferenceLoader referenceLoader = mock(SchedulingReferenceLoader.class);
        SchedulePlanExplainService explainService = mock(SchedulePlanExplainService.class);
        ScheduleThresholdProperties thresholdProperties = mock(ScheduleThresholdProperties.class);
        EngineContextLoader engineContextLoader = mock(EngineContextLoader.class);
        PerformanceBaselineService performanceBaselineService = mock(PerformanceBaselineService.class);

        TeachingTask teachingTask = new TeachingTask();
        teachingTask.setId(101L);
        teachingTask.setSemesterId(1L);
        teachingTask.setStatus(1);
        when(teachingTaskMapper.selectList(any())).thenReturn(List.of(teachingTask));
        when(planMapper.insert(any(SchedulePlan.class))).thenAnswer(invocation -> {
            SchedulePlan plan = invocation.getArgument(0);
            plan.setId(900L);
            return 1;
        });
        when(planMapper.updateById(any(SchedulePlan.class))).thenReturn(1);
        when(planItemMapper.insertBatch(anyList())).thenReturn(1);
        when(engineContextLoader.load(1L)).thenReturn(singleTaskContext());
        doAnswer(invocation -> {
            SchedulePlan plan = invocation.getArgument(0);
            plan.setTotalScore(BigDecimal.valueOf(100));
            return null;
        }).when(scoreService).rescore(any(SchedulePlan.class));

        V3ScheduleGenerateService service = new V3ScheduleGenerateService(
                semesterService,
                ruleService,
                scoreService,
                planMapper,
                planItemMapper,
                teachingTaskMapper,
                referenceLoader,
                explainService,
                thresholdProperties,
                engineContextLoader,
                performanceBaselineService
        );
        ScheduleGenerateRequest request = new ScheduleGenerateRequest();
        request.setSemesterId(1L);
        request.setStrategyType("SOLVER_V8");
        request.setSolverSeed(7L);
        request.setSolverTimeBudgetMs(500L);

        ScheduleGenerateResult result = service.generate(request);

        assertEquals(900L, result.getPlanId());
        assertEquals("SOLVER_V8", result.getStrategyType());
        assertEquals(1, result.getScheduledCount());
        assertEquals(0, result.getUnscheduledCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchedulePlanItem>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(planItemMapper).insertBatch(batchCaptor.capture());
        assertEquals(1, batchCaptor.getValue().size());
        SchedulePlanItem item = batchCaptor.getValue().get(0);
        assertEquals(900L, item.getPlanId());
        assertEquals(101L, item.getTeachingTaskId());
        assertEquals(401L, item.getTeacherId());
        assertEquals(501L, item.getClassId());
        assertEquals(601L, item.getCourseId());
        assertEquals(301L, item.getClassroomId());
        assertEquals(1, item.getWeekday());
        assertEquals(1, item.getStartPeriod());
        assertEquals(2, item.getEndPeriod());

        verifyNoInteractions(referenceLoader);
        verify(performanceBaselineService).recordSafely(
                eq(PerformanceBaselineService.OP_V8_SOLVER_GENERATE),
                eq(1L),
                eq(900L),
                isNull(),
                eq(1),
                eq(1),
                anyLong(),
                eq(true),
                isNull(),
                isNull(),
                argThat((String extra) -> extra != null
                        && extra.contains("\"timeBudgetMs\":1000")
                        && extra.contains("\"optimizeTimeBudgetMs\":10000")));
    }

    private EngineContext singleTaskContext() {
        return new EngineContext(
                List.of(new EngineTask(0, 101L, 0, 0, 0, 1, "NORMAL", 30, List.of(0), "ODD")),
                List.of(new EngineContext.TimeSlotData(0, 201L, 1, 1, "ODD")),
                List.of(new EngineContext.ClassroomData(0, 301L, 60, "NORMAL")),
                List.of(new EngineContext.TeacherData(0, 401L, "T1", 1)),
                List.of(new EngineContext.ClassData(0, 501L, 30, 1)),
                List.of(new EngineContext.CourseData(0, 601L, "NORMAL")),
                new boolean[1][1],
                new boolean[1],
                new boolean[1],
                new boolean[1],
                4,
                4,
                false,
                5,
                Map.of(),
                List.of(),
                List.of(),
                new int[1]);
    }

    /**
     * V9 阶段1：V8 引擎对 weekType!=ALL 的任务显式拒绝（不进引擎），落 unassigned，
     * reasonCode=WEEK_TYPE_NOT_SUPPORTED_BY_SOLVER_V8。ALL 任务正常排课。
     */
    @Test
    void solverV8RejectsNonAllWeekTypeTasksToUnassigned() {
        SemesterService semesterService = mock(SemesterService.class);
        ScheduleRuleService ruleService = mock(ScheduleRuleService.class);
        ScheduleScoreService scoreService = mock(ScheduleScoreService.class);
        SchedulePlanMapper planMapper = mock(SchedulePlanMapper.class);
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        SchedulingReferenceLoader referenceLoader = mock(SchedulingReferenceLoader.class);
        SchedulePlanExplainService explainService = mock(SchedulePlanExplainService.class);
        ScheduleThresholdProperties thresholdProperties = mock(ScheduleThresholdProperties.class);
        EngineContextLoader engineContextLoader = mock(EngineContextLoader.class);
        PerformanceBaselineService performanceBaselineService = mock(PerformanceBaselineService.class);

        // 两个任务：101L=ALL（引擎正常排）、102L=ODD（被拦截落 unassigned）
        TeachingTask allTask = new TeachingTask();
        allTask.setId(101L);
        allTask.setSemesterId(1L);
        allTask.setStatus(1);
        allTask.setWeekType("ALL");
        TeachingTask oddTask = new TeachingTask();
        oddTask.setId(102L);
        oddTask.setSemesterId(1L);
        oddTask.setStatus(1);
        oddTask.setWeekType("ODD");
        when(teachingTaskMapper.selectList(any())).thenReturn(List.of(allTask, oddTask));
        when(planMapper.insert(any(SchedulePlan.class))).thenAnswer(invocation -> {
            SchedulePlan plan = invocation.getArgument(0);
            plan.setId(900L);
            return 1;
        });
        when(planMapper.updateById(any(SchedulePlan.class))).thenReturn(1);
        when(planItemMapper.insertBatch(anyList())).thenReturn(1);
        // loader 只装载 ALL 任务（ODD 在 loader 内部也被跳过，ctx 仅含 101L）
        when(engineContextLoader.load(1L)).thenReturn(singleTaskContext());
        doAnswer(invocation -> {
            SchedulePlan plan = invocation.getArgument(0);
            plan.setTotalScore(BigDecimal.valueOf(100));
            return null;
        }).when(scoreService).rescore(any(SchedulePlan.class));

        V3ScheduleGenerateService service = new V3ScheduleGenerateService(
                semesterService,
                ruleService,
                scoreService,
                planMapper,
                planItemMapper,
                teachingTaskMapper,
                referenceLoader,
                explainService,
                thresholdProperties,
                engineContextLoader,
                performanceBaselineService
        );
        ScheduleGenerateRequest request = new ScheduleGenerateRequest();
        request.setSemesterId(1L);
        request.setStrategyType("SOLVER_V8");
        request.setSolverSeed(7L);
        request.setSolverTimeBudgetMs(500L);

        ScheduleGenerateResult result = service.generate(request);

        // ALL 任务 101L 正常排课
        assertEquals(1, result.getScheduledCount(), "ALL 任务应被正常排课");
        // ODD 任务 102L 被拒绝计入未排
        assertEquals(1, result.getUnscheduledCount(), "ODD 任务应计入未排数");

        // ODD 任务落 unassigned，reasonCode 正确
        verify(explainService).saveUnassignedTask(
                eq(900L), eq(1L), eq(102L),
                eq("WEEK_TYPE_NOT_SUPPORTED_BY_SOLVER_V8"),
                contains("单双周"),
                anyString());
        // ALL 任务不应被拒绝
        verify(explainService, never()).saveUnassignedTask(
                eq(900L), eq(1L), eq(101L),
                anyString(), anyString(), anyString());
    }
}
