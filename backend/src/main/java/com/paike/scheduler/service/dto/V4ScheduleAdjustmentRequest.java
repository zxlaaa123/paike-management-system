package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class V4ScheduleAdjustmentRequest {

    @NotBlank(message = "调整目标类型不能为空")
    private String targetType;

    private Long planId;

    private Long planItemId;

    private Long scheduleId;

    @NotNull(message = "新星期不能为空")
    @Min(value = 1, message = "星期取值必须在 1-7 之间")
    private Integer newWeekDay;

    @NotNull(message = "新开始节次不能为空")
    @Min(value = 1, message = "开始节次必须大于 0")
    private Integer newPeriodStart;

    @NotNull(message = "新结束节次不能为空")
    @Min(value = 1, message = "结束节次必须大于 0")
    private Integer newPeriodEnd;

    @NotNull(message = "新教室不能为空")
    private Long newRoomId;

    private String adjustReason;

    private Boolean forceAdjust;
}
