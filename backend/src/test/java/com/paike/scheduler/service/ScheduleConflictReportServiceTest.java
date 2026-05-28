package com.paike.scheduler.service;

import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleConflictReport;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleConflictReportMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeacherUnavailableTimeMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleConflictReportServiceTest {

    @Test
    void generate_persistsResolvedSemesterOnConflictReports() {
        ScheduleConflictReportMapper conflictReportMapper = mock(ScheduleConflictReportMapper.class);
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        TeacherMapper teacherMapper = mock(TeacherMapper.class);
        ClassInfoMapper classInfoMapper = mock(ClassInfoMapper.class);
        ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        TeacherUnavailableTimeMapper unavailableTimeMapper = mock(TeacherUnavailableTimeMapper.class);
        ScheduleRuleService ruleService = mock(ScheduleRuleService.class);
        ScheduleConflictReportService service = new ScheduleConflictReportService(
                conflictReportMapper,
                scheduleMapper,
                teachingTaskMapper,
                teacherMapper,
                classInfoMapper,
                classroomMapper,
                courseMapper,
                timeSlotMapper,
                unavailableTimeMapper,
                ruleService,
                mock(SemesterService.class));

        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(1L, 2L, 10L, 101L, 201L),
                schedule(2L, 2L, 10L, 102L, 202L)));
        when(teachingTaskMapper.selectList(any())).thenReturn(List.of());
        when(teacherMapper.selectList(any())).thenReturn(List.of(teacher(10L)));
        when(classInfoMapper.selectList(any())).thenReturn(List.of(classInfo(101L), classInfo(102L)));
        when(classroomMapper.selectList(any())).thenReturn(List.of(classroom(201L), classroom(202L)));
        when(courseMapper.selectList(any())).thenReturn(List.of(course(301L), course(302L)));
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(timeSlot(901L)));
        when(unavailableTimeMapper.selectList(any())).thenReturn(List.of());
        when(ruleService.getIntValue(any())).thenReturn(99);

        service.generate(2L);

        ArgumentCaptor<ScheduleConflictReport> captor = ArgumentCaptor.forClass(ScheduleConflictReport.class);
        verify(conflictReportMapper).insert(captor.capture());
        assertEquals(2L, captor.getValue().getSemesterId());
        assertEquals("TEACHER_CONFLICT", captor.getValue().getConflictType());
    }

    private Schedule schedule(Long id, Long semesterId, Long teacherId, Long classId, Long classroomId) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setSemesterId(semesterId);
        schedule.setTeacherId(teacherId);
        schedule.setClassId(classId);
        schedule.setClassroomId(classroomId);
        schedule.setCourseId(300L + id);
        schedule.setTimeSlotId(901L);
        schedule.setDeleted(0);
        return schedule;
    }

    private Teacher teacher(Long id) {
        Teacher teacher = new Teacher();
        teacher.setId(id);
        teacher.setName("教师" + id);
        return teacher;
    }

    private ClassInfo classInfo(Long id) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(id);
        classInfo.setClassName("班级" + id);
        classInfo.setStudentCount(30);
        return classInfo;
    }

    private Classroom classroom(Long id) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setRoomName("教室" + id);
        classroom.setCapacity(50);
        classroom.setRoomType("NORMAL");
        return classroom;
    }

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        course.setCourseName("课程" + id);
        course.setCourseType("NORMAL");
        return course;
    }

    private TimeSlot timeSlot(Long id) {
        TimeSlot slot = new TimeSlot();
        slot.setId(id);
        slot.setDayOfWeek(1);
        slot.setPeriodNo(1);
        return slot;
    }
}
