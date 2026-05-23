package com.paike.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "app.schedule.thresholds")
public class ScheduleThresholdProperties {
    /** 教师周课时中度超载阈值 */
    private int teacherOverloadMedium = 18;
    /** 教师周课时高度超载阈值 */
    private int teacherOverloadHigh = 22;
    /** 班级日课时中度超载阈值 */
    private int classDailyOverloadMedium = 8;
    /** 班级日课时高度超载阈值 */
    private int classDailyOverloadHigh = 10;
    /** 教室利用率偏低阈值（百分比） */
    private BigDecimal roomLowUtilization = BigDecimal.valueOf(30);
    /** 教室利用率偏高阈值（百分比） */
    private BigDecimal roomHighUtilization = BigDecimal.valueOf(85);
    /** 一周可排总节次（按大节计） */
    private int totalAvailablePeriods = 20;
    /** 下午起始节次（startPeriod >= 此值算下午） */
    private int afternoonStartPeriod = 5;
}
