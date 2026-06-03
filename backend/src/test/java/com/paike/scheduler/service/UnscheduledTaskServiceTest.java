package com.paike.scheduler.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.UnscheduledTaskMapper;
import com.paike.scheduler.service.vo.UnscheduledTaskVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnscheduledTaskServiceTest {

    private UnscheduledTaskMapper unscheduledTaskMapper;
    private UnscheduledTaskService service;

    @BeforeEach
    void setUp() {
        unscheduledTaskMapper = mock(UnscheduledTaskMapper.class);
        service = new UnscheduledTaskService(unscheduledTaskMapper, mock(TeachingTaskMapper.class));
    }

    @Test
    void list_usesDatabasePaginationForRelationFilters() {
        Page<UnscheduledTaskVo> expected = new Page<>(2, 5);
        when(unscheduledTaskMapper.selectFilteredPage(
                eq(7L), eq("高数"), eq("张"), eq("软件"), eq("CAPACITY"), any()))
                .thenReturn(expected);

        Page<UnscheduledTaskVo> result = service.list(7L, "高数", "张", "软件", "CAPACITY", 2, 5);

        assertSame(expected, result);
        verify(unscheduledTaskMapper).selectFilteredPage(
                eq(7L), eq("高数"), eq("张"), eq("软件"), eq("CAPACITY"),
                argThat(p -> p.getCurrent() == 2 && p.getSize() == 5));
    }

    @Test
    void list_treatsBlankFiltersAsAbsent() {
        Page<UnscheduledTaskVo> expected = new Page<>(1, 10);
        when(unscheduledTaskMapper.selectFilteredPage(
                eq(7L), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(expected);

        Page<UnscheduledTaskVo> result = service.list(7L, " ", "", null, "\t", 1, 10);

        assertSame(expected, result);
    }

    @Test
    void paginationQueryFiltersRelatedNamesInDatabase() throws IOException {
        String serviceSource = Files.readString(
                Path.of("src", "main", "java", "com", "paike", "scheduler", "service", "UnscheduledTaskService.java"),
                StandardCharsets.UTF_8);
        String mapperXml = Files.readString(
                Path.of("src", "main", "resources", "mapper", "UnscheduledTaskMapper.xml"),
                StandardCharsets.UTF_8);

        assertFalse(serviceSource.contains("subList("));
        assertFalse(serviceSource.contains("unscheduledTaskMapper.selectList("));
        assertTrue(mapperXml.contains("selectFilteredPage"));
        assertTrue(mapperXml.contains("c.course_name LIKE"));
        assertTrue(mapperXml.contains("t.name LIKE"));
        assertTrue(mapperXml.contains("cl.class_name LIKE"));
    }
}
