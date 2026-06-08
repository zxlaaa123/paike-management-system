package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.entity.ScheduleConsistencyCheck;
import com.paike.scheduler.mapper.ScheduleConsistencyCheckMapper;
import com.paike.scheduler.service.vo.V5ConsistencyCheckReportVo;
import com.paike.scheduler.service.vo.V5ConsistencyIssueVo;
import com.paike.scheduler.service.vo.V6ConsistencyCheckDetailVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V6ConsistencyCheckServiceTest {

    private ScheduleConsistencyCheckMapper consistencyCheckMapper;
    private V5ConsistencyCheckService v5ConsistencyCheckService;
    private V6ConsistencyCheckService service;

    @BeforeEach
    void setUp() {
        consistencyCheckMapper = mock(ScheduleConsistencyCheckMapper.class);
        v5ConsistencyCheckService = mock(V5ConsistencyCheckService.class);
        service = new V6ConsistencyCheckService(consistencyCheckMapper, v5ConsistencyCheckService, new ObjectMapper());
    }

    @Test
    void list_appliesPaginationAndReturnsMapperResult() {
        Page<ScheduleConsistencyCheck> expected = new Page<>(2, 20);
        when(consistencyCheckMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(expected);

        Page<ScheduleConsistencyCheck> result = service.list(" fail ", " V5_SIMULATION ", 3L, 9L, 2, 20);

        assertEquals(expected, result);
        ArgumentCaptor<Page<ScheduleConsistencyCheck>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(consistencyCheckMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(20, pageCaptor.getValue().getSize());
    }

    @Test
    void getById_parsesIssueListFromDetailJson() throws Exception {
        V5ConsistencyIssueVo issue = new V5ConsistencyIssueVo();
        issue.setCode("LOCKED_ITEM_MOVED");
        issue.setSeverity("BLOCKING");
        V5ConsistencyCheckReportVo report = new V5ConsistencyCheckReportVo();
        report.setIssues(List.of(issue));

        ScheduleConsistencyCheck record = new ScheduleConsistencyCheck();
        record.setId(7L);
        record.setDetailJson(new ObjectMapper().writeValueAsString(report));
        when(consistencyCheckMapper.selectById(7L)).thenReturn(record);

        V6ConsistencyCheckDetailVo result = service.getById(7L);

        assertNotNull(result.getRecord());
        assertEquals(7L, result.getRecord().getId());
        assertEquals(1, result.getIssues().size());
        assertEquals("LOCKED_ITEM_MOVED", result.getIssues().get(0).getCode());
    }

    @Test
    void run_delegatesToV5ConsistencyCheck() {
        V5ConsistencyCheckReportVo expected = new V5ConsistencyCheckReportVo();
        when(v5ConsistencyCheckService.check(4L, 8L, true)).thenReturn(expected);

        V5ConsistencyCheckReportVo result = service.run(4L, 8L);

        assertEquals(expected, result);
        verify(v5ConsistencyCheckService).check(4L, 8L, true);
    }
}

