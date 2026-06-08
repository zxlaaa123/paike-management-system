package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleConsistencyCheck;
import com.paike.scheduler.service.V6ConsistencyCheckService;
import com.paike.scheduler.service.vo.V5ConsistencyCheckReportVo;
import com.paike.scheduler.service.vo.V6ConsistencyCheckDetailVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v6/consistency-checks")
@RequiredArgsConstructor
public class V6ConsistencyCheckController {

    private final V6ConsistencyCheckService consistencyCheckService;

    @GetMapping
    public Result<Page<ScheduleConsistencyCheck>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long planId,
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            @RequestParam(defaultValue = "1") int page,
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(consistencyCheckService.list(status, checkType, semesterId, planId, page, size));
    }

    @GetMapping("/{id}")
    public Result<V6ConsistencyCheckDetailVo> getById(@PathVariable Long id) {
        return Result.success(consistencyCheckService.getById(id));
    }

    @PostMapping("/run")
    public Result<V5ConsistencyCheckReportVo> run(
            @RequestParam Long taskId,
            @RequestParam Long planId
    ) {
        return Result.success("一致性检查完成", consistencyCheckService.run(taskId, planId));
    }
}

