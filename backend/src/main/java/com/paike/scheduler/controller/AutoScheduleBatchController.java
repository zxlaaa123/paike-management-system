package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.AutoScheduleBatch;
import com.paike.scheduler.service.AutoScheduleBatchService;
import com.paike.scheduler.service.AutoScheduleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auto-schedule")
@RequiredArgsConstructor
public class AutoScheduleBatchController {

    private final AutoScheduleBatchService batchService;
    private final AutoScheduleService autoScheduleService;

    /** 查询自动排课批次列表 */
    @GetMapping("/batches")
    public Result<Page<AutoScheduleBatch>> listBatches(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(batchService.list(batchNo, status, page, size));
    }

    /** 查询批次详情 */
    @GetMapping("/batches/{batchId}")
    public Result<AutoScheduleBatch> getBatchById(@PathVariable Long batchId) {
        AutoScheduleBatch batch = batchService.getById(batchId);
        if (batch == null) {
            return Result.fail(404, "批次不存在");
        }
        return Result.success(batch);
    }

    /** 执行自动排课 */
    @PostMapping("/run")
    public Result<AutoScheduleService.AutoScheduleResult> run(@RequestBody AutoScheduleRequest request) {
        AutoScheduleService.AutoScheduleRequest serviceRequest = new AutoScheduleService.AutoScheduleRequest();
        serviceRequest.setTaskIds(request.getTaskIds());
        serviceRequest.setClearOldAutoSchedule(request.isClearOldAutoSchedule());
        serviceRequest.setClearAllSchedule(request.isClearAllSchedule());
        AutoScheduleService.AutoScheduleResult result = autoScheduleService.run(serviceRequest);
        return Result.success(result);
    }

    /** 清空某批次的自动排课结果 */
    @DeleteMapping("/batches/{batchId}/schedules")
    public Result<Void> clearBatchSchedules(@PathVariable Long batchId) {
        batchService.deleteBatchSchedules(batchId);
        return Result.success("清空成功", null);
    }

    @Data
    public static class AutoScheduleRequest {
        private java.util.List<Long> taskIds;
        private boolean clearOldAutoSchedule = true;
        private boolean clearAllSchedule = false;
    }
}
