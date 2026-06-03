package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.SchedulePlanService;
import com.paike.scheduler.service.dto.SchedulePlanItemAdjustRequest;
import com.paike.scheduler.service.vo.AdjustPlanResultVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/schedule-plan-items")
@RequiredArgsConstructor
public class SchedulePlanItemController {

    private final SchedulePlanService planService;

    @PutMapping("/{itemId}/adjust")
    public Result<AdjustPlanResultVo> adjust(
            @PathVariable Long itemId,
            @Valid @RequestBody SchedulePlanItemAdjustRequest request
    ) {
        return Result.success("调整成功", planService.adjustPlanItem(itemId, request));
    }
}
