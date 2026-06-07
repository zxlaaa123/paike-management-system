package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.AutoScheduleBatch;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.service.dto.AutoScheduleRequest;
import com.paike.scheduler.service.scheduling.SchedulingReferenceLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoScheduleServiceAuditTest {

    @Test
    void run_recordsFailureAuditWhenCurrentSemesterMissing() {
        SemesterService semesterService = mock(SemesterService.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        when(semesterService.getCurrentSemester()).thenThrow(new BusinessException("未找到当前学期"));
        AutoScheduleService service = new AutoScheduleService(
                mock(AutoScheduleBatchService.class),
                mock(UnscheduledTaskService.class),
                mock(ScheduleConflictService.class),
                mock(ScheduleRuleService.class),
                mock(ScheduleMapper.class),
                mock(TeachingTaskMapper.class),
                mock(SchedulingReferenceLoader.class),
                semesterService,
                mock(ScheduleLockGuardService.class),
                auditLogService);
        AutoScheduleRequest request = new AutoScheduleRequest();

        assertThrows(BusinessException.class, () -> service.run(request));

        verify(auditLogService).recordFailure(
                eq(SystemAuditLogService.ACTION_RUN_AUTO_SCHEDULE),
                eq(SystemAuditLogService.TARGET_AUTO_SCHEDULE_BATCH),
                eq(null),
                eq(null),
                eq(null),
                eq(SystemAuditLogService.ERROR_BUSINESS),
                any());
    }

    @Test
    void run_recordsFailureAuditWithoutRolledBackBatchTarget() {
        AutoScheduleBatchService batchService = mock(AutoScheduleBatchService.class);
        SchedulingReferenceLoader referenceLoader = mock(SchedulingReferenceLoader.class);
        SemesterService semesterService = mock(SemesterService.class);
        SystemAuditLogService auditLogService = mock(SystemAuditLogService.class);
        TeachingTaskMapper teachingTaskMapper = mock(TeachingTaskMapper.class);
        Semester semester = new Semester();
        semester.setId(3L);
        AutoScheduleBatch batch = new AutoScheduleBatch();
        batch.setId(88L);
        batch.setSemesterId(3L);
        batch.setBatchNo("AUTO88");
        when(semesterService.getCurrentSemester()).thenReturn(semester);
        when(teachingTaskMapper.selectList(any())).thenReturn(List.of());
        when(batchService.createBatch(3L, 0, false)).thenReturn(batch);
        when(referenceLoader.loadForAutoSchedule()).thenThrow(new BusinessException("加载排课参考数据失败"));
        AutoScheduleService service = new AutoScheduleService(
                batchService,
                mock(UnscheduledTaskService.class),
                mock(ScheduleConflictService.class),
                mock(ScheduleRuleService.class),
                mock(ScheduleMapper.class),
                teachingTaskMapper,
                referenceLoader,
                semesterService,
                mock(ScheduleLockGuardService.class),
                auditLogService);
        AutoScheduleRequest request = new AutoScheduleRequest();

        assertThrows(BusinessException.class, () -> service.run(request));

        verify(auditLogService).recordFailure(
                eq(SystemAuditLogService.ACTION_RUN_AUTO_SCHEDULE),
                eq(SystemAuditLogService.TARGET_AUTO_SCHEDULE_BATCH),
                eq(null),
                eq(3L),
                eq(null),
                eq(SystemAuditLogService.ERROR_BUSINESS),
                any());
    }
}
