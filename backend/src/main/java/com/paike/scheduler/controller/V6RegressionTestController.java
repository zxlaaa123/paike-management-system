package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleRegressionTest;
import com.paike.scheduler.service.V6RegressionTestService;
import com.paike.scheduler.service.vo.V6RegressionRunResultVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v6/regression-tests")
@RequiredArgsConstructor
public class V6RegressionTestController {

    private final V6RegressionTestService regressionTestService;

    @GetMapping
    public Result<Page<ScheduleRegressionTest>> list(
            @RequestParam(required = false) String testStage,
            @RequestParam(required = false) String testSuite,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long planId,
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            @RequestParam(defaultValue = "1") int page,
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(regressionTestService.list(testStage, testSuite, status, semesterId, planId, page, size));
    }

    @GetMapping("/{id}")
    public Result<ScheduleRegressionTest> getById(@PathVariable Long id) {
        return Result.success(regressionTestService.getById(id));
    }

    /**
     * 执行回归自检：对指定学期（不传取当前学期）正式课表跑一致性扫描并落库。
     * 写操作，经 AuthInterceptor 校验需 ADMIN。
     */
    @PostMapping("/run")
    public Result<V6RegressionRunResultVo> run(@RequestParam(required = false) Long semesterId) {
        return Result.success(regressionTestService.run(semesterId));
    }
}

