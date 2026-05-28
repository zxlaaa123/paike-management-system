package com.paike.scheduler.service;

import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.dto.SchedulePlanItemAdjustRequest;
import com.paike.scheduler.service.dto.V4ScheduleAdjustmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class V4ScheduleAdjustmentServiceTest {

    @Test
    void toPlanAdjustRequest_usesPreviewReasonWhenAdjustReasonMissing() throws Exception {
        V4ScheduleAdjustmentService service = new V4ScheduleAdjustmentService(
                mock(SchedulePlanService.class),
                mock(SchedulePlanExplainService.class),
                mock(SchedulePlanMapper.class),
                mock(SchedulePlanItemMapper.class),
                mock(ScheduleMapper.class),
                mock(TimeSlotMapper.class),
                mock(ClassroomMapper.class),
                mock(CourseMapper.class),
                mock(TeacherMapper.class),
                mock(ClassInfoMapper.class),
                mock(ScheduleLockGuardService.class),
                mock(TeacherUnavailableTimeService.class),
                mock(TransactionTemplate.class));
        V4ScheduleAdjustmentRequest request = new V4ScheduleAdjustmentRequest();

        Method method = V4ScheduleAdjustmentService.class
                .getDeclaredMethod("toPlanAdjustRequest", V4ScheduleAdjustmentRequest.class);
        method.setAccessible(true);
        SchedulePlanItemAdjustRequest result = (SchedulePlanItemAdjustRequest) method.invoke(service, request);

        assertEquals("调整预检", result.getAdjustReason());
    }
}
