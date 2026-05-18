package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V4ScheduleLockService;
import com.paike.scheduler.service.dto.ScheduleLockRequest;
import com.paike.scheduler.service.vo.ScheduleLockActionVo;
import com.paike.scheduler.service.vo.ScheduleLockListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v4/schedule-locks")
@RequiredArgsConstructor
public class ScheduleLockController {

    private final V4ScheduleLockService scheduleLockService;

    @PostMapping("/lock")
    public Result<ScheduleLockActionVo> lock(@RequestBody ScheduleLockRequest request) {
        ScheduleLockActionVo result = scheduleLockService.lock(request);
        return Result.success("课程已锁定", result);
    }

    @PostMapping("/unlock")
    public Result<ScheduleLockActionVo> unlock(@RequestBody ScheduleLockRequest request) {
        ScheduleLockActionVo result = scheduleLockService.unlock(request);
        return Result.success("课程已取消锁定", result);
    }

    @GetMapping("/plans/{planId}")
    public Result<ScheduleLockListVo> listPlanLocks(@PathVariable Long planId) {
        return Result.success(scheduleLockService.listPlanLocks(planId));
    }
}
