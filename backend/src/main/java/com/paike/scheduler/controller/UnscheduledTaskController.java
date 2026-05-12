package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.UnscheduledTask;
import com.paike.scheduler.service.UnscheduledTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/unscheduled-tasks")
@RequiredArgsConstructor
public class UnscheduledTaskController {

    private final UnscheduledTaskService unscheduledTaskService;

    /** 查询未排任务列表 */
    @GetMapping
    public Result<Page<UnscheduledTask>> list(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String reasonType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(unscheduledTaskService.list(batchId, courseName, teacherName, className, reasonType, page, size));
    }

    /** 按批次查询未排任务 */
    @GetMapping("/batch/{batchId}")
    public Result<Page<UnscheduledTask>> listByBatch(
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(unscheduledTaskService.list(batchId, null, null, null, null, page, size));
    }

    /** 清空未排任务记录 */
    @DeleteMapping
    public Result<Void> clear(@RequestParam(required = false) Long batchId) {
        if (batchId != null) {
            unscheduledTaskService.clearByBatchId(batchId);
        } else {
            unscheduledTaskService.clearAll();
        }
        return Result.success("清空成功", null);
    }
}
