package com.paike.scheduler.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SemesterRequest {

    @NotBlank(message = "学期名称不能为空")
    private String name;

    @NotBlank(message = "学年不能为空")
    private String schoolYear;

    @NotBlank(message = "学期不能为空")
    private String term;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private String remark;
}
