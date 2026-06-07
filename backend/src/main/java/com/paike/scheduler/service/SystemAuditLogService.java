package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.auth.AuthUserContext;
import com.paike.scheduler.entity.SysUser;
import com.paike.scheduler.entity.SystemAuditLog;
import com.paike.scheduler.mapper.SystemAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SystemAuditLogService {

    public static final String ACTION_APPLY_PLAN = "APPLY_PLAN";
    public static final String ACTION_ROLLBACK_PLAN = "ROLLBACK_PLAN";
    public static final String ACTION_LOCK_PLAN_ITEM = "LOCK_PLAN_ITEM";
    public static final String ACTION_UNLOCK_PLAN_ITEM = "UNLOCK_PLAN_ITEM";
    public static final String ACTION_LOCK_SCHEDULE = "LOCK_SCHEDULE";
    public static final String ACTION_UNLOCK_SCHEDULE = "UNLOCK_SCHEDULE";
    public static final String ACTION_CREATE_SCHEDULE = "CREATE_SCHEDULE";
    public static final String ACTION_DELETE_SCHEDULE = "DELETE_SCHEDULE";
    public static final String ACTION_ADJUST_SCHEDULE = "ADJUST_SCHEDULE";
    public static final String ACTION_APPLY_SIMULATION_PLAN = "APPLY_SIMULATION_PLAN";
    public static final String ACTION_CREATE_LOCAL_REPLAN_PLAN = "CREATE_LOCAL_REPLAN_PLAN";
    public static final String ACTION_GENERATE_SIMULATION_PLAN = "GENERATE_SIMULATION_PLAN";
    public static final String ACTION_GENERATE_LOCAL_REPLAN_SIMULATION = "GENERATE_LOCAL_REPLAN_SIMULATION";
    public static final String ACTION_RUN_AUTO_SCHEDULE = "RUN_AUTO_SCHEDULE";
    public static final String ERROR_BUSINESS = "BUSINESS_ERROR";
    public static final String ERROR_SYSTEM = "SYSTEM_ERROR";
    public static final String TARGET_SCHEDULE_PLAN = "SCHEDULE_PLAN";
    public static final String TARGET_SCHEDULE_PLAN_ITEM = "SCHEDULE_PLAN_ITEM";
    public static final String TARGET_SCHEDULE = "SCHEDULE";
    public static final String TARGET_AUTO_SCHEDULE_BATCH = "AUTO_SCHEDULE_BATCH";

    private final SystemAuditLogMapper auditLogMapper;

    public static String auditErrorCode(RuntimeException ex) {
        return ex instanceof BusinessException ? ERROR_BUSINESS : ERROR_SYSTEM;
    }

    public Page<SystemAuditLog> list(String actionType, Long semesterId, Long planId, Boolean success,
                                     int page, int size) {
        LambdaQueryWrapper<SystemAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (actionType != null && !actionType.isBlank()) {
            wrapper.eq(SystemAuditLog::getActionType, actionType);
        }
        if (semesterId != null) {
            wrapper.eq(SystemAuditLog::getSemesterId, semesterId);
        }
        if (planId != null) {
            wrapper.eq(SystemAuditLog::getPlanId, planId);
        }
        if (success != null) {
            wrapper.eq(SystemAuditLog::getSuccess, success ? 1 : 0);
        }
        wrapper.orderByDesc(SystemAuditLog::getCreatedAt).orderByDesc(SystemAuditLog::getId);
        return auditLogMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public SystemAuditLog getById(Long id) {
        return auditLogMapper.selectById(id);
    }

    public void recordSuccess(String actionType, String targetType, Long targetId, Long semesterId,
                              Long planId, String afterSummary) {
        SystemAuditLog log = newLogWithOperator();
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setSemesterId(semesterId);
        log.setPlanId(planId);
        log.setSuccess(1);
        log.setAfterSummary(afterSummary);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String actionType, String targetType, Long targetId, Long semesterId,
                              Long planId, String errorCode, String errorMessage) {
        SystemAuditLog log = newLogWithOperator();
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setSemesterId(semesterId);
        log.setPlanId(planId);
        log.setSuccess(0);
        log.setErrorCode(errorCode);
        log.setErrorMessage(errorMessage);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    private SystemAuditLog newLogWithOperator() {
        SysUser operator = AuthUserContext.get();
        SystemAuditLog log = new SystemAuditLog();
        if (operator != null) {
            log.setOperatorId(operator.getId());
            log.setOperatorName(operator.getRealName() == null || operator.getRealName().isBlank()
                    ? operator.getUsername()
                    : operator.getRealName());
        }
        return log;
    }
}
