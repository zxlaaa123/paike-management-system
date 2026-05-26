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
 * 两套用途不同（贪心选下一步 vs 评估整体方案），故意保留双轨；本表从一条 Dimension record
 * 升级为 sealed interface + 三种类型，编译期强制区分在线/离线/硬约束三轨道。
 *
 * <h2>已知现象（非 bug，D3 已决策）</h2>
 * MORNING_THEORY_PRIORITY 在 COMPREHENSIVE 默认权重表里没有，但在线 scoreCandidate 仍然会调
 * weight(refData, "MORNING_THEORY_PRIORITY") —— 从 weightMap 取不到走默认 0，相当于该维度在
 * COMPREHENSIVE 策略下"隐式失效"。产品决策为不补默认权重：COMPREHENSIVE 保持综合均衡，
 * 早课偏好仅由 CLASS_BALANCE 等明确配置该规则的策略启用。
 */
public final class ScoringDimensions {

    private ScoringDimensions() {}

    public sealed interface Dimension permits OnlineSoft, OfflineSoft, OfflineHard {
        String code();
        String displayName();
        String usage();
    }

    public record OnlineSoft(
            String code,
            String displayName,
            String formula,
            String range,
            String usage
    ) implements Dimension {}

    public record OfflineSoft(
            String code,
            String displayName,
            String formula,
            String range,
            String usage
    ) implements Dimension {}

    public record OfflineHard(
            String code,
            String displayName,
            String formula,
            String range,
            String usage
    ) implements Dimension {}

    /**
     * 6 个在线软维度 —— 用于在线 scoreCandidate 的候选打分文档对照。
     */
    public static final List<OnlineSoft> ONLINE_SOFT = List.of(
            new OnlineSoft(
                    "CLASSROOM_UTILIZATION",
                    "教室利用率",
                    "studentCount / capacity，超容也允许 >1（贪心更偏好『刚好坐满』）",
                    "[0, N)",
                    "在线偏好高利用率单个房间；离线惩罚启用教室全集使用不均"),
            new OnlineSoft(
                    "CLASS_DAILY_BALANCE",
                    "班级每日均衡",
                    "1 / (1 + 同班当天已排数) —— 越后排越不愿意再加",
                    "(0, 1]",
                    "在线打压『同班同天扎堆』；离线惩罚『日数方差大』（跨周比较）"),
            new OnlineSoft(
                    "TEACHER_DAILY_LOAD",
                    "教师每日负载",
                    "1 / (1 + 同教师当天已排数)",
                    "(0, 1]",
                    "在线/离线公式都用 variancePenalty，但在线是『当天累计』，离线是『跨日方差』"),
            new OnlineSoft(
                    "COURSE_DISTRIBUTION",
                    "课程分布均衡",
                    "存在同班同课同日 ? 0 : 1 —— 二值",
                    "{0, 1}",
                    "在线是『同一天不再排』的硬偏好；离线是『全 plan 重复天数占比』"),
            new OnlineSoft(
                    "CONTINUOUS_PERIOD_LIMIT",
                    "连续上课限制",
                    "相邻节次 (|Δperiod|==2) 且同教师/同班 ? 0 : 1 —— 二值",
                    "{0, 1}",
                    "在线打压『紧挨着排』；离线衡量『连续链平均长度』"),
            new OnlineSoft(
                    "MORNING_THEORY_PRIORITY",
                    "理论课优先上午",
                    "理论课（非 EXPERIMENT/COMPUTER）&& periodNo<=2 ? 1 : 0",
                    "{0, 1}",
                    "COMPREHENSIVE 策略默认权重表无此项，相当于在线维度失效；CLASS_BALANCE 等策略有"));

    /**
     * 6 个离线软维度 —— 用于离线 rescore 写库的罚分文档对照。
     */
    public static final List<OfflineSoft> OFFLINE_SOFT = List.of(
            new OfflineSoft(
                    "CLASSROOM_UTILIZATION",
                    "教室利用率",
                    "启用教室使用次数方差（未使用按 0 计），归一为 min(1, variance / avg²)",
                    "[0, 1]",
                    "在线偏好高利用率单个房间；离线惩罚启用教室全集使用不均"),
            new OfflineSoft(
                    "CLASS_DAILY_BALANCE",
                    "班级每日均衡",
                    "班级每日数方差均值，归一 min(1, variance/4)",
                    "[0, 1]",
                    "在线打压『同班同天扎堆』；离线惩罚『日数方差大』（跨周比较）"),
            new OfflineSoft(
                    "TEACHER_DAILY_LOAD",
                    "教师每日负载",
                    "同 CLASS_DAILY_BALANCE，按 teacherId 分组",
                    "[0, 1]",
                    "在线/离线公式都用 variancePenalty，但在线是『当天累计』，离线是『跨日方差』"),
            new OfflineSoft(
                    "COURSE_DISTRIBUTION",
                    "课程分布均衡",
                    "(同班同课同日次数>1 的天数) / 总(班×课×天) 数",
                    "[0, 1]",
                    "在线是『同一天不再排』的硬偏好；离线是『全 plan 重复天数占比』"),
            new OfflineSoft(
                    "CONTINUOUS_PERIOD_LIMIT",
                    "连续上课限制",
                    "教师每日 startPeriod 排序后相邻差==2 的链数，min(1, chains/2) 求样本均值",
                    "[0, 1]",
                    "在线打压『紧挨着排』；离线衡量『连续链平均长度』"),
            new OfflineSoft(
                    "MORNING_THEORY_PRIORITY",
                    "理论课优先上午",
                    "全部下午课占比（不区分课程类型！）—— 离线公式更粗",
                    "[0, 1]",
                    "COMPREHENSIVE 策略默认权重表无此项，相当于在线维度失效；CLASS_BALANCE 等策略有"));

    /**
     * 6 个离线硬维度 —— 只在离线 rescore 出现，违规一次扣 weight × 1。
     * 在线 scoreCandidate 不参与（硬约束由 conflictService 提前拦截，根本进不到打分这步）。
     */
    public static final List<OfflineHard> OFFLINE_HARD = List.of(
            new OfflineHard(
                    "TEACHER_TIME_CONFLICT", "教师时间冲突",
                    "同一 (teacherId, weekday, startPeriod) 出现 >1 条，每多 1 条算 1 次违规",
                    "[0, ∞)",
                    "硬约束，理论上 plan 内应为 0"),
            new OfflineHard(
                    "CLASS_TIME_CONFLICT", "班级时间冲突",
                    "同 (classId, weekday, startPeriod) >1 条",
                    "[0, ∞)",
                    "硬约束"),
            new OfflineHard(
                    "CLASSROOM_TIME_CONFLICT", "教室时间冲突",
                    "同 (classroomId, weekday, startPeriod) >1 条",
                    "[0, ∞)",
                    "硬约束"),
            new OfflineHard(
                    "TEACHER_UNAVAILABLE", "教师禁排时间",
                    "item.conflictReason 包含 TEACHER_UNAVAILABLE 标签的数量",
                    "[0, ∞)",
                    "靠 SchedulePlanItem.conflictReason 字符串解析，依赖前置标记"),
            new OfflineHard(
                    "CLASSROOM_CAPACITY", "教室容量不足",
                    "item.conflictReason 包含 CLASSROOM_CAPACITY 标签的数量",
                    "[0, ∞)",
                    "靠 conflictReason 解析"),
            new OfflineHard(
                    "CLASSROOM_TYPE_MISMATCH", "教室类型不匹配",
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
