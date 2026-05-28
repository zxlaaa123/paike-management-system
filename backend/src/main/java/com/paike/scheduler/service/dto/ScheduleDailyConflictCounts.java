package com.paike.scheduler.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleDailyConflictCounts {

    private Long teacherDaily;

    private Long classDaily;

    private Long sameCourse;

    public long teacherDailyOrZero() {
        return teacherDaily == null ? 0L : teacherDaily;
    }

    public long classDailyOrZero() {
        return classDaily == null ? 0L : classDaily;
    }

    public long sameCourseOrZero() {
        return sameCourse == null ? 0L : sameCourse;
    }
}
