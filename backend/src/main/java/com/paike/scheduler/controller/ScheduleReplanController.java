package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V4ScheduleReplanService;
import com.paike.scheduler.service.dto.V4ScheduleReplanRequest;
import com.paike.scheduler.service.vo.ScheduleReplanResultVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v4/schedule-replan")
@RequiredArgsConstructor
public class ScheduleReplanController {

    private final V4ScheduleReplanService scheduleReplanService;

    @PostMapping("/plans/{planId}")
    public Result<ScheduleReplanResultVo> createLocalReplanPlan(
            @PathVariable Long planId,
            @RequestBody(required = false) V4ScheduleReplanRequest request
    ) {
        ScheduleReplanResultVo result = scheduleReplanService.createLocalReplanPlan(planId, request);
        return Result.success(result);
    }
}
