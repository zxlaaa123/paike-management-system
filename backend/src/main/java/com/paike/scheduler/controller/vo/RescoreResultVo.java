package com.paike.scheduler.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 方案重新评分响应（M-14 收敛：替换 ScheduleScoreController.rescore 原先的 Map 弱类型返回）。
 * 字段与历史 JSON 完全一致：planId / totalScore / conflictCount / scoreLevel。
 *
 * 顺带消除原 {@code Map.of(...)} 在 totalScore / conflictCount 为 null 时抛 NPE 的隐患。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RescoreResultVo {

    private Long planId;

    private BigDecimal totalScore;

    private Integer conflictCount;

    private String scoreLevel;
}
