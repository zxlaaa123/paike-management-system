package com.paike.scheduler.service;

import com.paike.scheduler.mapper.*;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.service.dto.SchedulePlanItemAdjustRequest;
import com.paike.scheduler.service.dto.V4ScheduleAdjustmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V4ScheduleAdjustmentServiceTest {

    @Test
    void toPlanAdjustRequest_usesPreviewReasonWhenAdjustReasonMissing() throws Exception {
        V4ScheduleAdjustmentService service = newService(mock(TransactionTemplate.class), mock(SystemAuditLogService.class));
        V4ScheduleAdjustmentRequest request = new V4ScheduleAdjustmentRequest();

        Method method = V4ScheduleAdjustmentService.class
                .getDeclaredMethod("toPlanAdjustRequest", V4ScheduleAdjustmentRequest.class);
        method.setAccessible(true);
        SchedulePlanItemAdjustRequest result = (SchedulePlanItemAdjustRequest) method.invoke(service, request);

        assertEquals("调整预检", result.getAdjustReason());
    }

    @Test
    void applyAdjustment_recordsFailureAuditWhenRejected() {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        V4ScheduleAdjustmentService service = newService(transactionTemplate, auditLogService);
        V4ScheduleAdjustmentRequest request = new V4ScheduleAdjustmentRequest();
        request.setTargetType("SCHEDULE");
        request.setScheduleId(7L);
        request.setPlanId(9L);

        assertThrows(BusinessException.class, () -> service.applyAdjustment(request));

        verify(auditLogService).recordFailure(
                eq(SystemAuditLogService.ACTION_ADJUST_SCHEDULE),
                eq(SystemAuditLogService.TARGET_SCHEDULE),
                eq(7L),
                eq(null),
                eq(9L),
                eq("BUSINESS_ERROR"),
                any());
    }

    private V4ScheduleAdjustmentService newService(TransactionTemplate transactionTemplate,
                                                   SystemAuditLogService auditLogService) {
        return new V4ScheduleAdjustmentService(
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
                transactionTemplate,
                auditLogService);
    }
}
