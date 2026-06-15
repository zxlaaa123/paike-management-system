package com.paike.scheduler.service;

import java.util.List;

/**
 * 周次类型（单双周）支持工具，V9 阶段 1 引入。
 *
 * 统一封装 weekType 的归一化与冲突矩阵判定，被 DB 版冲突检测
 * ({@link ScheduleConflictService})、V3 贪心版冲突检测
 * ({@link V3ScheduleGenerateService})、以及两路对拍单测共用，
 * 保证两条检测链路的矩阵语义完全一致（V9_04 R1 红线）。
 *
 * V9 阶段 2A 起再加 {@link #countableWeekTypes(String)}（β 评分独立计数），
 * 被 {@link com.paike.scheduler.service.ScheduleScoreService}（离线 rescore）与
 * {@link com.paike.scheduler.service.scheduling.DeltaPenaltyScorer}（在线贪心增量）共用，
 * 保证评分双轨的展开语义一致。
 *
 * 语义见 V9_00 第 5 节裁决：方案 A，合法值 {ALL, ODD, EVEN}。
 * null / 空 视为 ALL（与 DB DEFAULT 'ALL'、存量回填语义一致）。
 */
public final class WeekTypeSupport {

    public static final String ALL = "ALL";
    public static final String ODD = "ODD";
    public static final String EVEN = "EVEN";

    private WeekTypeSupport() {
    }

    /**
     * 归一化 weekType：null / 空 / 纯空白 → "ALL"；非空则 trim + 大写。
     * 不校验合法性（非法值由入参校验层负责），仅做格式收敛。
     */
    public static String normalize(String weekType) {
        if (weekType == null || weekType.isBlank()) {
            return ALL;
        }
        return weekType.trim().toUpperCase();
    }

    /**
     * 冲突矩阵判定：两条排课记录在同一物理时段是否真冲突。
     * <p>矩阵（V9_00 第 5 节，3×3）：
     * <pre>
     *          existing: ALL  ODD  EVEN
     *   this ALL          ✓    ✓    ✓
     *   this ODD          ✓    ✓    ✗
     *   this EVEN         ✓    ✗    ✓
     * </pre>
     * 即 ALL 与任意冲突；ODD 与 EVEN 不冲突；其余（同周次 / 含 ALL）冲突。
     * null / 非法值按 ALL 处理（与 normalize 一致）。
     *
     * @return true 表示两条记录在同一时段会冲突，false 表示可共存（如单双周同槽）
     */
    public static boolean overlap(String a, String b) {
        String wa = normalize(a);
        String wb = normalize(b);
        if (ALL.equals(wa) || ALL.equals(wb)) {
            return true;
        }
        // 到这里 wa/wb ∈ {ODD, EVEN}：同周次冲突，不同周次（ODD vs EVEN）不冲突
        return wa.equals(wb);
    }

    /**
     * V9 阶段 2A β 评分（独立计数）：把一条排课记录展开为它"实际占用的周次类型集合"，
     * 用于负荷均衡按周次独立分桶聚合。
     * <p>展开规则（V9_00 §5 β 裁决 235-243 行）：
     * <ul>
     *   <li>ALL（全周）→ [ODD, EVEN]，全周课同时计入单周负荷与双周负荷</li>
     *   <li>ODD（单周）→ [ODD]</li>
     *   <li>EVEN（双周）→ [EVEN]</li>
     * </ul>
     * 被 {@link com.paike.scheduler.service.ScheduleScoreService}（离线 rescore）与
     * {@link com.paike.scheduler.service.scheduling.DeltaPenaltyScorer}（在线贪心增量）共用，
     * 保证评分双轨展开语义一致，避免在线/离线漂移。
     *
     * @return 该记录在 β 计数下应被计入的周次类型集合（至少含 1 个元素）
     */
    public static List<String> countableWeekTypes(String weekType) {
        String w = normalize(weekType);
        return ALL.equals(w) ? List.of(ODD, EVEN) : List.of(w);
    }

    /**
     * V9 阶段 2B 导出/网格显示：weekType 的中文显示标记。
     * <p>规则（与产品术语一致，非技术 ODD/EVEN）：
     * <ul>
     *   <li>ALL（全周）→ {@code ""}（不加标记，不污染现有全周课显示）</li>
     *   <li>ODD（单周）→ {@code "单"}</li>
     *   <li>EVEN（双周）→ {@code "双"}</li>
     * </ul>
     * 调用方拼成 {@code "体育[单]"} / {@code "思政[双]"}，ALL 课直接 {@code "体育"}（无后缀）。
     * 被 {@link com.paike.scheduler.service.TimetableService}（Excel 导出 + 网格 cell 文本）使用。
     */
    public static String displayLabel(String weekType) {
        String w = normalize(weekType);
        if (ODD.equals(w)) {
            return "单";
        }
        if (EVEN.equals(w)) {
            return "双";
        }
        return "";
    }
}
