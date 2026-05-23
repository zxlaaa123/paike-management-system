package com.paike.scheduler.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ScheduleRuleWeightBatchForm {

    @NotEmpty
    @Valid
    private List<Item> rules;

    @Data
    public static class Item {

        @NotNull
        @Positive
        private Long id;

        @NotNull
        @DecimalMin("0.0")
        private BigDecimal weight;

        @NotNull
        @Min(0)
        @Max(1)
        private Integer enabled;

        @Size(max = 255)
        private String description;
    }
}
