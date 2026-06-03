package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleGenerateLog;
import com.paike.scheduler.entity.ScheduleUnassignedTask;
import com.paike.scheduler.service.ScheduleCompareService;
import com.paike.scheduler.service.SchedulePlanExplainService;
import com.paike.scheduler.service.SchedulePlanService;
import com.paike.scheduler.service.SemesterService;
import com.paike.scheduler.service.vo.UnassignedSummaryVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v3/schedule-plans")
@RequiredArgsConstructor
@Slf4j
public class SchedulePlanController {

    private final SchedulePlanService planService;
    private final SemesterService semesterService;
    private final ScheduleCompareService compareService;
    private final SchedulePlanExplainService explainService;

    @GetMapping
    public Result<Page<SchedulePlan>> list(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String strategyType,
            @RequestParam(required = false) String keyword,
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            @RequestParam(defaultValue = "1") int page,
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
            @RequestParam(defaultValue = "10") int size
    ) {
        Long resolvedSemesterId = semesterId;
        if (resolvedSemesterId == null) {
            try {
                resolvedSemesterId = semesterService.getCurrentSemester().getId();
            } catch (BusinessException e) {
                log.warn("未找到当前学期，排课方案列表按业务约定返回空分页，前端显示空列表", e);
                return Result.success(new Page<>(page, size));
            }
        }
        return Result.success(planService.list(resolvedSemesterId, status, strategyType, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<SchedulePlan> getById(@PathVariable Long id) {
        return Result.success(planService.getById(id));
    }

    @GetMapping("/{planId}/items")
    public Result<List<SchedulePlanItem>> getItems(@PathVariable Long planId) {
        return Result.success(planService.getPlanItems(planId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/abandon")
    public Result<Void> abandon(@PathVariable Long id) {
        planService.abandon(id);
        return Result.success("已废弃", null);
    }

    /**
     * 方案对比
     */
    @PostMapping("/compare")
    public Result<Map<String, Object>> compare(@Valid @RequestBody CompareRequest request) {
        Long resolvedSemesterId = request.getSemesterId();
        if (resolvedSemesterId == null) {
            resolvedSemesterId = semesterService.getCurrentSemester().getId();
        }
        return Result.success(compareService.compare(resolvedSemesterId, request.getPlanIds()));
    }

    /**
     * 应用方案为正式课表
     */
    @PostMapping("/{id}/apply")
    public Result<Map<String, Object>> apply(@PathVariable Long id) {
        return Result.success("方案已应用为正式课表", planService.applyPlan(id));
    }

    /**
     * 回滚到历史方案（重新应用）
     */
    @PostMapping("/{id}/rollback")
    public Result<Map<String, Object>> rollback(@PathVariable Long id) {
        return Result.success("已回滚到该方案", planService.rollbackPlan(id));
    }

    @GetMapping("/{planId}/logs")
    public Result<List<ScheduleGenerateLog>> getLogs(
            @PathVariable Long planId,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) Long teachingTaskId
    ) {
        return Result.success(explainService.listPlanLogs(planId, logLevel, logType, teachingTaskId));
    }

    @GetMapping("/{planId}/tasks/{taskId}/logs")
    public Result<List<ScheduleGenerateLog>> getTaskLogs(@PathVariable Long planId, @PathVariable Long taskId) {
        return Result.success(explainService.listTaskLogs(planId, taskId));
    }

    @GetMapping("/{planId}/unassigned-tasks")
    public Result<List<ScheduleUnassignedTask>> getUnassignedTasks(@PathVariable Long planId) {
        return Result.success(explainService.listUnassignedTasks(planId));
    }

    @GetMapping("/{planId}/unassigned-summary")
    public Result<List<UnassignedSummaryVo>> getUnassignedSummary(@PathVariable Long planId) {
        return Result.success(explainService.summarizeUnassignedTasks(planId));
    }

    @lombok.Data
    public static class CompareRequest {
        private Long semesterId;

        @NotEmpty(message = "方案ID不能为空")
        private List<Long> planIds;
    }
}
