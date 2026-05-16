package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SchedulePlanItemAdjustRequest {

    @NotNull(message = "教室不能为空")
    private Long classroomId;

    @NotNull(message = "星期不能为空")
    @Min(value = 1, message = "星期取值必须在 1-7 之间")
    private Integer weekday;

    @NotNull(message = "开始节次不能为空")
    @Min(value = 1, message = "开始节次必须大于 0")
    private Integer startPeriod;

    @NotNull(message = "结束节次不能为空")
    @Min(value = 1, message = "结束节次必须大于 0")
    private Integer endPeriod;

    @NotBlank(message = "调整原因不能为空")
    private String adjustReason;
}
