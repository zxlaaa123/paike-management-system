package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.ScheduleRegressionTest;
import com.paike.scheduler.mapper.ScheduleRegressionTestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class V6RegressionTestService {

    private final ScheduleRegressionTestMapper regressionTestMapper;

    public Page<ScheduleRegressionTest> list(
            String testStage,
            String testSuite,
            String status,
            Long semesterId,
            Long planId,
            int page,
            int size
    ) {
        LambdaQueryWrapper<ScheduleRegressionTest> wrapper = new LambdaQueryWrapper<>();
        if (hasText(testStage)) {
            wrapper.eq(ScheduleRegressionTest::getTestStage, testStage.trim());
        }
        if (hasText(testSuite)) {
            wrapper.eq(ScheduleRegressionTest::getTestSuite, testSuite.trim());
        }
        if (hasText(status)) {
            wrapper.eq(ScheduleRegressionTest::getStatus, status.trim().toUpperCase());
        }
        if (semesterId != null) {
            wrapper.eq(ScheduleRegressionTest::getSemesterId, semesterId);
        }
        if (planId != null) {
            wrapper.eq(ScheduleRegressionTest::getPlanId, planId);
        }
        wrapper.orderByDesc(ScheduleRegressionTest::getExecutedAt)
                .orderByDesc(ScheduleRegressionTest::getId);
        return regressionTestMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public ScheduleRegressionTest getById(Long id) {
        return regressionTestMapper.selectById(id);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

