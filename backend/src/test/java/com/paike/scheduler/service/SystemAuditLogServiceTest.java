package com.paike.scheduler.service;

import com.paike.scheduler.auth.AuthUserContext;
import com.paike.scheduler.entity.SysUser;
import com.paike.scheduler.entity.SystemAuditLog;
import com.paike.scheduler.mapper.SystemAuditLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemAuditLogServiceTest {

    private SystemAuditLogMapper auditLogMapper;
    private SystemAuditLogService service;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(SystemAuditLogMapper.class);
        service = new SystemAuditLogService(auditLogMapper);
    }

    @AfterEach
    void tearDown() {
        AuthUserContext.clear();
    }

    @Test
    void recordSuccess_includesAuthenticatedOperatorAndTarget() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("admin");
        user.setRealName("管理员");
        AuthUserContext.set(user);

        service.recordSuccess(
                SystemAuditLogService.ACTION_APPLY_PLAN,
                SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                11L,
                3L,
                11L,
                "正式课表已应用，排课数=20");

        SystemAuditLog inserted = captureInserted();
        assertEquals(7L, inserted.getOperatorId());
        assertEquals("管理员", inserted.getOperatorName());
        assertEquals("APPLY_PLAN", inserted.getActionType());
        assertEquals("SCHEDULE_PLAN", inserted.getTargetType());
        assertEquals(11L, inserted.getTargetId());
        assertEquals(3L, inserted.getSemesterId());
        assertEquals(11L, inserted.getPlanId());
        assertEquals(1, inserted.getSuccess());
        assertEquals("正式课表已应用，排课数=20", inserted.getAfterSummary());
        assertNotNull(inserted.getCreatedAt());
    }

    @Test
    void recordSuccess_allowsSystemOperationWithoutAuthenticatedUser() {
        service.recordSuccess(
                SystemAuditLogService.ACTION_ROLLBACK_PLAN,
                SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                12L,
                3L,
                12L,
                "正式课表已应用，排课数=18");

        SystemAuditLog inserted = captureInserted();
        assertNull(inserted.getOperatorId());
        assertNull(inserted.getOperatorName());
        assertEquals("ROLLBACK_PLAN", inserted.getActionType());
    }

    @Test
    void recordFailure_recordsErrorFieldsAndOperator() {
        SysUser user = new SysUser();
        user.setId(8L);
        user.setUsername("operator");
        AuthUserContext.set(user);

        service.recordFailure(
                SystemAuditLogService.ACTION_APPLY_PLAN,
                SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                11L,
                3L,
                11L,
                "400",
                "已废弃方案不能应用");

        SystemAuditLog inserted = captureInserted();
        assertEquals(8L, inserted.getOperatorId());
        assertEquals("operator", inserted.getOperatorName());
        assertEquals("APPLY_PLAN", inserted.getActionType());
        assertEquals("SCHEDULE_PLAN", inserted.getTargetType());
        assertEquals(11L, inserted.getTargetId());
        assertEquals(3L, inserted.getSemesterId());
        assertEquals(11L, inserted.getPlanId());
        assertEquals(0, inserted.getSuccess());
        assertEquals("400", inserted.getErrorCode());
        assertEquals("已废弃方案不能应用", inserted.getErrorMessage());
        assertNotNull(inserted.getCreatedAt());
    }

    @Test
    void recordFailure_usesIndependentTransaction() throws NoSuchMethodException {
        Method method = SystemAuditLogService.class.getMethod(
                "recordFailure",
                String.class, String.class, Long.class, Long.class, Long.class, String.class, String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    private SystemAuditLog captureInserted() {
        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
