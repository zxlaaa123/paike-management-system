package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.SystemAuditLog;
import com.paike.scheduler.service.SystemAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v6/audit-logs")
@RequiredArgsConstructor
public class SystemAuditLogController {

    private final SystemAuditLogService auditLogService;

    @GetMapping
    public Result<Page<SystemAuditLog>> list(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Boolean success,
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            @RequestParam(defaultValue = "1") int page,
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(auditLogService.list(actionType, semesterId, planId, success, page, size));
    }

    @GetMapping("/{id}")
    public Result<SystemAuditLog> getById(@PathVariable Long id) {
        return Result.success(auditLogService.getById(id));
    }
}
