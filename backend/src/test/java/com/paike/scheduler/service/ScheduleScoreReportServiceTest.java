package com.paike.scheduler.service;

import com.paike.scheduler.common.enums.V5RepairTaskStatus;
import com.paike.scheduler.entity.ScheduleScoreReport;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduleScoreReportServiceTest {

    private ScheduleScoreReportMapper scoreReportMapper;
    private ScheduleMapper scheduleMapper;
    private TeachingTaskMapper teachingTaskMapper;
    private ClassInfoMapper classInfoMapper;
    private ClassroomMapper classroomMapper;
    private CourseMapper courseMapper;
    private TimeSlotMapper timeSlotMapper;
    private TeacherUnavailableTimeMapper unavailableTimeMapper;
    private ScheduleRuleService ruleService;
    private SemesterService semesterService;
    private ScheduleScoreReportService service;

    @BeforeEach
    void setUp() {
        scoreReportMapper = mock(ScheduleScoreReportMapper.class);
        scheduleMapper = mock(ScheduleMapper.class);
        teachingTaskMapper = mock(TeachingTaskMapper.class);
        classInfoMapper = mock(ClassInfoMapper.class);
        classroomMapper = mock(ClassroomMapper.class);
        courseMapper = mock(CourseMapper.class);
        timeSlotMapper = mock(TimeSlotMapper.class);
        unavailableTimeMapper = mock(TeacherUnavailableTimeMapper.class);
        ruleService = mock(ScheduleRuleService.class);
        semesterService = mock(SemesterService.class);
        service = new ScheduleScoreReportService(
                scoreReportMapper,
                scheduleMapper,
                teachingTaskMapper,
                classInfoMapper,
                classroomMapper,
                courseMapper,
                timeSlotMapper,
                unavailableTimeMapper,
                ruleService,
                semesterService);
    }

    @Test
    void generate_usesRequestedSemesterAndPersistsItOnReport() {
        Semester semester = new Semester();
        semester.setId(2L);
        when(semesterService.getById(2L)).thenReturn(semester);
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(teachingTaskMapper.selectList(any())).thenReturn(List.of());
        when(classInfoMapper.selectList(any())).thenReturn(List.of());
        when(classroomMapper.selectList(any())).thenReturn(List.of());
        when(courseMapper.selectList(any())).thenReturn(List.of());
        when(timeSlotMapper.selectList(any())).thenReturn(List.of());
        when(unavailableTimeMapper.selectList(any())).thenReturn(List.of());
        when(ruleService.getIntValue(any())).thenReturn(0);

        service.generate(2L);

        ArgumentCaptor<ScheduleScoreReport> captor = ArgumentCaptor.forClass(ScheduleScoreReport.class);
        verify(scoreReportMapper).insert(captor.capture());
        assertEquals(2L, captor.getValue().getSemesterId());
        verify(semesterService).getById(2L);
        verify(semesterService, never()).getCurrentSemester();
    }

    @Test
    void getLatest_defaultsToCurrentSemester() {
        Semester semester = new Semester();
        semester.setId(7L);
        when(semesterService.getCurrentSemester()).thenReturn(semester);
        ScheduleScoreReport report = new ScheduleScoreReport();
        report.setId(99L);
        report.setSemesterId(7L);
        report.setCreateTime(LocalDateTime.now());
        when(scoreReportMapper.selectOne(any())).thenReturn(report);

        ScheduleScoreReport result = service.getLatest();

        assertEquals(7L, result.getSemesterId());
        verify(semesterService).getCurrentSemester();
        verify(scoreReportMapper).selectOne(any());
    }

    @Test
    void pendingStatus_matchesDatabaseDefault() {
        assertEquals("PENDING", V5RepairTaskStatus.PENDING.getCode());
    }
}
