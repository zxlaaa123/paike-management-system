package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScheduleCompareServiceTest {

    private SchedulePlanMapper planMapper;
    private ScheduleScoreService scoreService;
    private ScheduleCompareService service;

    @BeforeEach
    void setUp() {
        planMapper = mock(SchedulePlanMapper.class);
        scoreService = mock(ScheduleScoreService.class);
        service = new ScheduleCompareService(planMapper, scoreService);
    }

    @Test
    void compare_rejectsDuplicatePlanIdsBeforeLoadingPlans() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.compare(1L, List.of(10L, 10L)));

        assertEquals("不能选择重复方案进行对比", error.getMessage());
        verifyNoInteractions(planMapper, scoreService);
    }

    @Test
    void compare_rejectsLessThanTwoPlans() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.compare(1L, List.of(10L)));

        assertEquals("至少需要选择两个方案进行对比", error.getMessage());
        verifyNoInteractions(planMapper, scoreService);
    }

    @Test
    void compare_rejectsPlanFromOtherSemester() {
        SchedulePlan first = plan(10L, 1L, "方案A", new BigDecimal("90"));
        SchedulePlan second = plan(11L, 2L, "方案B", new BigDecimal("95"));
        when(planMapper.selectById(10L)).thenReturn(first);
        when(planMapper.selectById(11L)).thenReturn(second);
        when(scoreService.getScoreDetails(anyLong())).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.compare(1L, List.of(10L, 11L)));

        assertEquals("方案 方案B 不属于当前学期", error.getMessage());
    }

    private SchedulePlan plan(Long id, Long semesterId, String name, BigDecimal totalScore) {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(id);
        plan.setSemesterId(semesterId);
        plan.setName(name);
        plan.setStrategyType("COMPREHENSIVE");
        plan.setTotalScore(totalScore);
        plan.setScheduledCount(1);
        plan.setUnscheduledCount(0);
        plan.setConflictCount(0);
        return plan;
    }
}
