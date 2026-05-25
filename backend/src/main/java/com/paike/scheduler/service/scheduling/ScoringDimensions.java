package com.paike.scheduler.service.scheduling;

import java.util.List;

/**
 * 评分维度登记表 —— 纯文档，不被算法读。
 *
 * <h2>为什么有这个文件</h2>
 * V3 评分体系是<b>双轨制</b>：同一规则码（如 CLASSROOM_UTILIZATION）在两个地方有<b>完全不同</b>的公式：
 * <ul>
 *   <li><b>在线 scoreCandidate</b>（V3ScheduleGenerateService.scoreCandidate）：贪心循环里给每个 (slot, room)
 *       候选打分，决定<i>选谁</i>。正向加权，越大越好。</li>
 *   <li><b>离线 rescore</b>（ScheduleScoreService.rescore）：方案生成完后给整张表打分，写入
 *       schedule_score_detail 和 schedule_plan.total_score —— 这才是用户看到的"90.13 分"的来源。
 *       反向罚分，从 100 扣。</li>
 * </ul>
 *
 * 两套用途不同（贪心选下一步 vs 评估整体方案），故意保留双轨；本表是为了让这种差异<b>可见</b>，
 * 避免未来读者把"在线 CLASSROOM_UTILIZATION"误以为就是 schedule_score_detail 里那一行。
 *
 * <h2>已知现象（非 bug，留作 D3 议题）</h2>
 * MORNING_THEORY_PRIORITY 在 COMPREHENSIVE 默认权重表里没有，但在线 scoreCandidate 仍然会调
 * weight(refData, "MORNING_THEORY_PRIORITY") —— 从 weightMap 取不到走默认 0，相当于该维度在
 * COMPREHENSIVE 策略下"隐式失效"。要不要补默认权重，是产品决策，不在 D2 范围。
 */
public final class ScoringDimensions {

    private ScoringDimensions() {}

    public record Dimension(
            String code,
            String displayName,
            String ruleType,
            String onlineFormula,
            String onlineRange,
            String offlineFormula,
            String offlineRange,
            String usage
    ) {}

    /**
     * 6 个软维度 —— 两套公式都有。
     * 硬维度（TEACHER_TIME_CONFLICT 等）只在离线 rescore 用，没有在线评分对应项，见 {@link #HARD}。
     */
    public static final List<Dimension> SOFT = List.of(
            new Dimension(
                    "CLASSROOM_UTILIZATION",
                    "教室利用率",
                    "SOFT",
                    "studentCount / capacity，超容也允许 >1（贪心更偏好『刚好坐满』）",
                    "[0, N)",
                    "教室使用次数方差，归一为 min(1, variance / avg²)",
                    "[0, 1]",
                    "在线偏好高利用率单个房间；离线惩罚教室之间使用不均"),
            new Dimension(
                    "CLASS_DAILY_BALANCE",
                    "班级每日均衡",
                    "SOFT",
                    "1 / (1 + 同班当天已排数) —— 越后排越不愿意再加",
                    "(0, 1]",
                    "班级每日数方差均值，归一 min(1, variance/4)",
                    "[0, 1]",
                    "在线打压『同班同天扎堆』；离线惩罚『日数方差大』（跨周比较）"),
            new Dimension(
                    "TEACHER_DAILY_LOAD",
                    "教师每日负载",
                    "SOFT",
                    "1 / (1 + 同教师当天已排数)",
                    "(0, 1]",
                    "同 CLASS_DAILY_BALANCE，按 teacherId 分组",
                    "[0, 1]",
                    "在线/离线公式都用 variancePenalty，但在线是『当天累计』，离线是『跨日方差』"),
            new Dimension(
                    "COURSE_DISTRIBUTION",
                    "课程分布均衡",
                    "SOFT",
                    "存在同班同课同日 ? 0 : 1 —— 二值",
                    "{0, 1}",
                    "(同班同课同日次数>1 的天数) / 总(班×课×天) 数",
                    "[0, 1]",
                    "在线是『同一天不再排』的硬偏好；离线是『全 plan 重复天数占比』"),
            new Dimension(
                    "CONTINUOUS_PERIOD_LIMIT",
                    "连续上课限制",
                    "SOFT",
                    "相邻节次 (|Δperiod|==2) 且同教师/同班 ? 0 : 1 —— 二值",
                    "{0, 1}",
                    "教师每日 startPeriod 排序后相邻差==2 的链数，min(1, chains/2) 求样本均值",
                    "[0, 1]",
                    "在线打压『紧挨着排』；离线衡量『连续链平均长度』"),
            new Dimension(
                    "MORNING_THEORY_PRIORITY",
                    "理论课优先上午",
                    "SOFT",
                    "理论课（非 EXPERIMENT/COMPUTER）&& periodNo<=2 ? 1 : 0",
                    "{0, 1}",
                    "全部下午课占比（不区分课程类型！）—— 离线公式更粗",
                    "[0, 1]",
                    "COMPREHENSIVE 策略默认权重表无此项，相当于在线维度失效；BALANCED 等策略有"));

    /**
     * 6 个硬维度 —— 只在离线 rescore 出现，违规一次扣 weight × 1。
     * 在线 scoreCandidate 不参与（硬约束由 conflictService 提前拦截，根本进不到打分这步）。
     */
    public static final List<Dimension> HARD = List.of(
            new Dimension(
                    "TEACHER_TIME_CONFLICT", "教师时间冲突", "HARD",
                    "N/A（在线由 ScheduleConflictService 拦截）", "—",
                    "同一 (teacherId, weekday, startPeriod) 出现 >1 条，每多 1 条算 1 次违规",
                    "[0, ∞)",
                    "硬约束，理论上 plan 内应为 0"),
            new Dimension(
                    "CLASS_TIME_CONFLICT", "班级时间冲突", "HARD",
                    "N/A", "—",
                    "同 (classId, weekday, startPeriod) >1 条",
                    "[0, ∞)",
                    "硬约束"),
            new Dimension(
                    "CLASSROOM_TIME_CONFLICT", "教室时间冲突", "HARD",
                    "N/A", "—",
                    "同 (classroomId, weekday, startPeriod) >1 条",
                    "[0, ∞)",
                    "硬约束"),
            new Dimension(
                    "TEACHER_UNAVAILABLE", "教师禁排时间", "HARD",
                    "N/A（在线 unavailableKeySet 直接 continue）", "—",
                    "item.conflictReason 包含 TEACHER_UNAVAILABLE 标签的数量",
                    "[0, ∞)",
                    "靠 SchedulePlanItem.conflictReason 字符串解析，依赖前置标记"),
            new Dimension(
                    "CLASSROOM_CAPACITY", "教室容量不足", "HARD",
                    "N/A（在线 matchedRooms 已按容量过滤）", "—",
                    "item.conflictReason 包含 CLASSROOM_CAPACITY 标签的数量",
                    "[0, ∞)",
                    "靠 conflictReason 解析"),
            new Dimension(
                    "CLASSROOM_TYPE_MISMATCH", "教室类型不匹配", "HARD",
                    "N/A（在线 matchedRooms 已按类型过滤）", "—",
                    "item.conflictReason 包含 CLASSROOM_TYPE_MISMATCH 标签的数量",
                    "[0, ∞)",
                    "靠 conflictReason 解析"));

    /**
     * 离线总分公式（ScheduleScoreService.rescore）：
     * <pre>
     *   totalScore = clamp(100 + Σ rule.score, 0, 100)
     *   软规则 score = - weight × min(1, penalty)        // penalty ∈ [0, 1]
     *   硬规则 score = - weight × violationCount         // violationCount ∈ [0, ∞)
     * </pre>
     *
     * 在线没有"总分"概念，只在贪心循环里挑分最大的候选。还有一个稳定 tie-breaker：
     * <pre>
     *   score += max(0, 100 - slot.sortOrder) × 0.0001
     * </pre>
     */
    public static final String TOTAL_SCORE_FORMULA_NOTE = "见 javadoc";
}
