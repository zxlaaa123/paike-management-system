package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeacherUnavailableTime;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeacherUnavailableTimeMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.dto.TeacherUnavailableTimeForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherUnavailableTimeServiceTest {

    @Test
    void create_rejectsDuplicateTeacherTimeSlotBeforeInsert() {
        TeacherUnavailableTimeMapper unavailableTimeMapper = mock(TeacherUnavailableTimeMapper.class);
        TeacherMapper teacherMapper = mock(TeacherMapper.class);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        TeacherUnavailableTimeService service = new TeacherUnavailableTimeService(
                unavailableTimeMapper, teacherMapper, timeSlotMapper);
        TeacherUnavailableTimeForm form = form();
        when(teacherMapper.selectById(1L)).thenReturn(activeTeacher());
        when(timeSlotMapper.selectById(2L)).thenReturn(timeSlot());
        when(unavailableTimeMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(form));

        assertEquals("张老师老师在周一第1大节已存在禁排时间", ex.getMessage());
        verify(unavailableTimeMapper, never()).insert(any(TeacherUnavailableTime.class));
    }

    @Test
    void update_rejectsDuplicateTeacherTimeSlotBeforeMutation() {
        TeacherUnavailableTimeMapper unavailableTimeMapper = mock(TeacherUnavailableTimeMapper.class);
        TeacherMapper teacherMapper = mock(TeacherMapper.class);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        TeacherUnavailableTimeService service = new TeacherUnavailableTimeService(
                unavailableTimeMapper, teacherMapper, timeSlotMapper);
        TeacherUnavailableTimeForm form = form();
        TeacherUnavailableTime existing = new TeacherUnavailableTime();
        existing.setId(9L);
        existing.setDeleted(0);
        when(unavailableTimeMapper.selectById(9L)).thenReturn(existing);
        when(teacherMapper.selectById(1L)).thenReturn(activeTeacher());
        when(timeSlotMapper.selectById(2L)).thenReturn(timeSlot());
        when(unavailableTimeMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(9L, form));

        assertEquals("张老师老师在周一第1大节已存在禁排时间", ex.getMessage());
        verify(unavailableTimeMapper, never()).updateById(any(TeacherUnavailableTime.class));
    }

    private TeacherUnavailableTimeForm form() {
        TeacherUnavailableTimeForm form = new TeacherUnavailableTimeForm();
        form.setTeacherId(1L);
        form.setTimeSlotId(2L);
        form.setReason("会议");
        form.setStatus(1);
        return form;
    }

    private Teacher activeTeacher() {
        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setName("张老师");
        teacher.setStatus(1);
        teacher.setDeleted(0);
        return teacher;
    }

    private TimeSlot timeSlot() {
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setId(2L);
        timeSlot.setTimeLabel("周一第1大节");
        return timeSlot;
    }
}
