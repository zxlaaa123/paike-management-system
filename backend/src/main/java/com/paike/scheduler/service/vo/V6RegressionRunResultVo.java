package com.paike.scheduler.service.vo;

import com.paike.scheduler.entity.ScheduleRegressionTest;
import lombok.Data;

import java.util.List;

/**
 * V6 回归测试中心「执行自检」结果汇总。
 * 自检对当前学期正式课表跑一组一致性扫描，每个扫描项写入一条 ScheduleRegressionTest 记录。
 */
@Data
public class V6RegressionRunResultVo {
    private Long semesterId;
    private Integer total;
    private Integer passed;
    private Integer failed;
    private Long durationMs;
    private String summary;
    private List<ScheduleRegressionTest> records;
}
