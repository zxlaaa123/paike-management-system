package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.mapper.AutoScheduleBatchMapper;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.ScheduleConflictService;
import com.paike.scheduler.service.ScheduleLockGuardService;
import com.paike.scheduler.service.ScheduleService;
import com.paike.scheduler.service.SemesterService;
import com.paike.scheduler.service.SystemAuditLogService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleControllerTest {

    @Test
    void list_trimsBlankKeywordFiltersBeforeMapperQuery() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        SemesterService semesterService = mock(SemesterService.class);
        ScheduleService scheduleService = new ScheduleService(
                scheduleMapper,
                mock(TeachingTaskMapper.class),
                mock(TimeSlotMapper.class),
                mock(ClassroomMapper.class),
                mock(CourseMapper.class),
                mock(TeacherMapper.class),
                mock(ClassInfoMapper.class),
                mock(ScheduleConflictService.class),
                mock(ScheduleLockGuardService.class),
                mock(AutoScheduleBatchMapper.class),
                semesterService,
                mock(SystemAuditLogService.class));
        Semester semester = new Semester();
        semester.setId(8L);
        when(semesterService.getCurrentSemester()).thenReturn(semester);
        when(scheduleMapper.selectFilteredSchedulePage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Page<Schedule>(1, 10));

        scheduleService.list("  数学  ", "   ", "\t一班\t", "", null, null, 1, 10);

        verify(scheduleMapper).selectFilteredSchedulePage(
                eq("数学"),
                eq(null),
                eq("一班"),
                eq(null),
                eq(null),
                eq(8L),
                any());
    }
}
