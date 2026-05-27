package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.dto.SchedulePlanItemAdjustRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SchedulePlanServiceTest {

    @Test
    void adjustPlanItem_rejectsMissingReasonBeforeMutation() {
        SchedulePlanItemMapper planItemMapper = mock(SchedulePlanItemMapper.class);
        SchedulePlanService service = new SchedulePlanService(
                mock(SchedulePlanMapper.class),
                planItemMapper,
                mock(ScheduleMapper.class),
                mock(ScheduleLockedItemMapper.class),
                mock(ScheduleLockGuardService.class),
                mock(CourseMapper.class),
                mock(TeacherMapper.class),
                mock(ClassInfoMapper.class),
                mock(ClassroomMapper.class),
                mock(TimeSlotMapper.class),
                mock(TeachingTaskMapper.class),
                mock(TeacherUnavailableTimeService.class),
                mock(ScheduleScoreService.class),
                mock(SchedulePlanExplainService.class),
                mock(SystemAuditLogService.class));

        SchedulePlanItemAdjustRequest request = new SchedulePlanItemAdjustRequest();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.adjustPlanItem(1L, request));

        assertEquals("调整原因不能为空", error.getMessage());
        verifyNoInteractions(planItemMapper);
    }
}
