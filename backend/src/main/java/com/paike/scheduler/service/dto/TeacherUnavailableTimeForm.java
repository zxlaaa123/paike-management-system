package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeacherUnavailableTimeForm {

    @NotNull
    @Positive
    private Long teacherId;

    @NotNull
    @Positive
    private Long timeSlotId;

    @Size(max = 255)
    private String reason;

    @Min(0)
    @Max(1)
    private Integer status;

    @Size(max = 255)
    private String remark;
}
