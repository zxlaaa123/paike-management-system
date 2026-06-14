package com.paike.scheduler.service;

/**
 * 周次类型（单双周）支持工具，V9 阶段 1 引入。
 *
 * 统一封装 weekType 的归一化与冲突矩阵判定，被 DB 版冲突检测
 * ({@link ScheduleConflictService})、V3 贪心版冲突检测
 * ({@link V3ScheduleGenerateService})、以及两路对拍单测共用，
 * 保证两条检测链路的矩阵语义完全一致（V9_04 R1 红线）。
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
}
