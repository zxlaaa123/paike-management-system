package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.ScheduleRegressionTest;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.ScheduleRegressionTestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V6RegressionTestServiceTest {

    private ScheduleRegressionTestMapper regressionTestMapper;
    private V6RegressionTestService service;

    @BeforeEach
    void setUp() {
        regressionTestMapper = mock(ScheduleRegressionTestMapper.class);
        service = new V6RegressionTestService(regressionTestMapper, mock(ScheduleMapper.class), mock(SemesterService.class));
    }

    @Test
    void list_appliesPaginationAndReturnsMapperResult() {
        Page<ScheduleRegressionTest> expected = new Page<>(2, 20);
        when(regressionTestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(expected);

        Page<ScheduleRegressionTest> result = service.list(
                " V5_STAGE11 ",
                " smoke ",
                " pass ",
                3L,
                9L,
                2,
                20);

        assertEquals(expected, result);
        ArgumentCaptor<Page<ScheduleRegressionTest>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(regressionTestMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(20, pageCaptor.getValue().getSize());
    }

    @Test
    void getById_delegatesToMapper() {
        ScheduleRegressionTest expected = new ScheduleRegressionTest();
        expected.setId(7L);
        when(regressionTestMapper.selectById(7L)).thenReturn(expected);

        ScheduleRegressionTest result = service.getById(7L);

        assertEquals(expected, result);
        verify(regressionTestMapper).selectById(7L);
    }
}

