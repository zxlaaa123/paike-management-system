package com.paike.scheduler.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 方案评分摘要响应（M-14 收敛：替换 ScheduleScoreController.getScoreSummary 原先的 Map 弱类型返回）。
 *
 * 字段与历史 JSON 完全一致（5 字段）：planId / totalScore / hardViolationCount / softViolationCount / scoreLevel。
 * 注：前端 ScoreSummary 类型另声明了 conflictCount 并用 {@code ?? plan.conflictCount} 兜底，
 * 后端历史上不发此字段；本次阶段1 维持现状（不补发），契约不一致点留待后续单独定案。
 *
 * 顺带消除原 {@code Map.of(...)} 在 totalScore 为 null 时抛 NPE 的隐患（getScoreLevel 本就兼容 null）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreSummaryVo {

    private Long planId;

    private BigDecimal totalScore;

    private Integer hardViolationCount;

    private Integer softViolationCount;

    private String scoreLevel;
}
