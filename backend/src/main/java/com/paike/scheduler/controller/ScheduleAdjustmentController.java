package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V4ScheduleAdjustmentService;
import com.paike.scheduler.service.dto.V4ScheduleAdjustmentRequest;
import com.paike.scheduler.service.vo.ScheduleAdjustmentApplyVo;
import com.paike.scheduler.service.vo.ScheduleAdjustmentCheckVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v4/schedule-adjustments")
@RequiredArgsConstructor
public class ScheduleAdjustmentController {

    private final V4ScheduleAdjustmentService adjustmentService;

    @PostMapping("/check")
    public Result<ScheduleAdjustmentCheckVo> check(@Valid @RequestBody V4ScheduleAdjustmentRequest request) {
        return Result.success(adjustmentService.checkAdjustment(request));
    }

    @PostMapping("/apply")
    public Result<ScheduleAdjustmentApplyVo> apply(@Valid @RequestBody V4ScheduleAdjustmentRequest request) {
        ScheduleAdjustmentApplyVo result = adjustmentService.applyAdjustment(request);
        String message = Boolean.TRUE.equals(result.getSaved()) ? "调整已保存" : "检测完成";
        return Result.success(message, result);
    }
}
