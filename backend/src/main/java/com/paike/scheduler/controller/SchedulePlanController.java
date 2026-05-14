package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.service.SchedulePlanService;
import com.paike.scheduler.service.SemesterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v3/schedule-plans")
@RequiredArgsConstructor
public class SchedulePlanController {

    private final SchedulePlanService planService;
    private final SemesterService semesterService;

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
}
