package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.controller.vo.HealthInfoVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Result<HealthInfoVo> health() {
        HealthInfoVo info = new HealthInfoVo("UP", "scheduler-backend", LocalDateTime.now());
        return Result.success("服务运行正常", info);
    }
}
