package com.paike.scheduler.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 健康检查响应（M-14 收敛：替换 HealthController 原先的 Map 弱类型返回）。
 * 字段与历史 JSON 完全一致：status / service / time。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthInfoVo {

    /** 服务状态，固定 "UP"。 */
    private String status;

    /** 服务名。 */
    private String service;

    /** 当前服务器时间。 */
    private LocalDateTime time;
}
