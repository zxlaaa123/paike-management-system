package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimetableServiceSemesterBoundaryTest {

    @Test
    void listTeacherTimetable_usesExplicitSemesterForTaskAndScheduleQueries() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        TimetableService service = newService(scheduleMapper, teachingTaskMapper, mock(SemesterService.class));
        when(teachingTaskMapper.selectList(any())).thenReturn(List.of());
        when(scheduleMapper.selectList(any())).thenReturn(List.of());

        service.listTeacherTimetable(20L, 3L);

        ArgumentCaptor<LambdaQueryWrapper<TeachingTask>> taskCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<Schedule>> scheduleCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(teachingTaskMapper).selectList(taskCaptor.capture());
        verify(scheduleMapper).selectList(scheduleCaptor.capture());
        assertWrapperContains(taskCaptor.getValue(), TeachingTask.class, "teacher_id", 20L);
        assertWrapperContains(taskCaptor.getValue(), TeachingTask.class, "semester_id", 3L);
        assertWrapperContains(scheduleCaptor.getValue(), Schedule.class, "teacher_id", 20L);
        assertWrapperContains(scheduleCaptor.getValue(), Schedule.class, "semester_id", 3L);
    }

    @Test
    void listClassroomTimetable_usesCurrentSemesterWhenRequestDoesNotProvideSemester() {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        SemesterService semesterService = mock(SemesterService.class);
        Semester semester = new Semester();
        semester.setId(4L);
        when(semesterService.getCurrentSemester()).thenReturn(semester);
        TimetableService service = newService(scheduleMapper, mock(TeachingTaskMapper.class), semesterService);
        when(scheduleMapper.selectList(any())).thenReturn(List.of());

        service.listClassroomTimetable(30L, null);

        ArgumentCaptor<LambdaQueryWrapper<Schedule>> scheduleCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(scheduleMapper).selectList(scheduleCaptor.capture());
        assertWrapperContains(scheduleCaptor.getValue(), Schedule.class, "classroom_id", 30L);
        assertWrapperContains(scheduleCaptor.getValue(), Schedule.class, "semester_id", 4L);
    }

    private TimetableService newService(
            ScheduleMapper scheduleMapper,
            TeachingTaskMapper teachingTaskMapper,
            SemesterService semesterService
    ) {
        return new TimetableService(
                scheduleMapper,
                teachingTaskMapper,
                mock(TimeSlotMapper.class),
                mock(ClassroomMapper.class),
                mock(CourseMapper.class),
                mock(TeacherMapper.class),
                mock(ClassInfoMapper.class),
                semesterService);
    }

    private void assertWrapperContains(LambdaQueryWrapper<?> wrapper, Class<?> entityType, String column, Object value) {
        ensureTableInfo(entityType);
        assertTrue(wrapper.getSqlSegment().contains(column), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(value),
                column + " value missing from " + wrapper.getParamNameValuePairs());
    }

    private void ensureTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
