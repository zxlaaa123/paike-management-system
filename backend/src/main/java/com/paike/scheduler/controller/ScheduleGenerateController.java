package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V3ScheduleGenerateService;
import com.paike.scheduler.service.dto.MultipleScheduleGenerateRequest;
import com.paike.scheduler.service.dto.ScheduleGenerateRequest;
import com.paike.scheduler.service.dto.ScheduleGenerateResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v3/schedule-generate")
@RequiredArgsConstructor
public class ScheduleGenerateController {

    private final V3ScheduleGenerateService generateService;

    @PostMapping
    public Result<ScheduleGenerateResult> generate(@Valid @RequestBody ScheduleGenerateRequest request) {
        return Result.success(generateService.generate(request));
    }

    @PostMapping("/multiple")
    public Result<List<ScheduleGenerateResult>> generateMultiple(@Valid @RequestBody MultipleScheduleGenerateRequest request) {
        return Result.success("多方案生成成功", generateService.generateMultiple(request));
    }
}
