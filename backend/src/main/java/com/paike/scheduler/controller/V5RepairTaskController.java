package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V5RepairTaskFlowService;
import com.paike.scheduler.service.dto.V5RepairTaskCancelRequest;
import com.paike.scheduler.service.dto.V5RepairTaskFlowCreateRequest;
import com.paike.scheduler.service.dto.V5RepairTaskStatusUpdateRequest;
import com.paike.scheduler.service.vo.V5RepairTaskDetailVo;
import com.paike.scheduler.service.vo.V5RepairTaskVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/repair-tasks")
@RequiredArgsConstructor
public class V5RepairTaskController {

    private final V5RepairTaskFlowService repairTaskFlowService;

    @PostMapping
    public Result<V5RepairTaskDetailVo> create(@Valid @RequestBody V5RepairTaskFlowCreateRequest request) {
        return Result.success("修复任务已创建", repairTaskFlowService.createTask(request));
    }

    @GetMapping
    public Result<List<V5RepairTaskVo>> list(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String status
    ) {
        return Result.success(repairTaskFlowService.listTasks(semesterId, planId, status));
    }

    @GetMapping("/{taskId}")
    public Result<V5RepairTaskDetailVo> detail(@PathVariable Long taskId) {
        return Result.success(repairTaskFlowService.getTask(taskId));
    }

    @PutMapping("/{taskId}/status")
    public Result<V5RepairTaskDetailVo> updateStatus(
            @PathVariable Long taskId,
            @Valid @RequestBody V5RepairTaskStatusUpdateRequest request
    ) {
        return Result.success("状态已更新", repairTaskFlowService.updateStatus(taskId, request));
    }

    @PostMapping("/{taskId}/cancel")
    public Result<V5RepairTaskDetailVo> cancel(@PathVariable Long taskId, @Valid @RequestBody(required = false) V5RepairTaskCancelRequest request) {
        String reason = request == null ? null : request.getReason();
        return Result.success("任务已取消", repairTaskFlowService.cancelTask(taskId, reason));
    }
}
