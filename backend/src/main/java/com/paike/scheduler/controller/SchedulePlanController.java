package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.service.ScheduleCompareService;
import com.paike.scheduler.service.SchedulePlanService;
import com.paike.scheduler.service.SemesterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/schedule-plans")
@RequiredArgsConstructor
public class SchedulePlanController {

    private final SchedulePlanService planService;
    private final SemesterService semesterService;
    private final ScheduleCompareService compareService;

    @GetMapping
    public Result<Page<SchedulePlan>> list(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String strategyType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long resolvedSemesterId = semesterId;
        if (resolvedSemesterId == null) {
            try {
                resolvedSemesterId = semesterService.getCurrentSemester().getId();
            } catch (BusinessException e) {
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
    public Result<Map<String, Object>> compare(@RequestBody Map<String, Object> request) {
        Long semesterId = request.get("semesterId") != null ? ((Number) request.get("semesterId")).longValue() : null;
        @SuppressWarnings("unchecked")
        List<Long> planIds = request.get("planIds") != null
                ? ((List<Object>) request.get("planIds")).stream().map(o -> ((Number) o).longValue()).toList()
                : null;

        if (semesterId == null) {
            semesterId = semesterService.getCurrentSemester().getId();
        }
        return Result.success(compareService.compare(semesterId, planIds));
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
}
