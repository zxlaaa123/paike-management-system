package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.DatabaseMigrationStatusService;
import com.paike.scheduler.service.vo.MigrationStatusOverviewVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v6/migrations")
@RequiredArgsConstructor
public class DatabaseMigrationStatusController {

    private final DatabaseMigrationStatusService databaseMigrationStatusService;

    @GetMapping("/status")
    public Result<MigrationStatusOverviewVo> status() {
        return Result.success(databaseMigrationStatusService.getStatus());
    }
}
