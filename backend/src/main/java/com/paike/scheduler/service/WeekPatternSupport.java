package com.paike.scheduler.service;

/**
 * V10 连续周段支持工具。
 *
 * <p>周模式由 weekType + startWeek + endWeek 组成。冲突判定不再只看
 * V9 的 ALL/ODD/EVEN 三值矩阵，而是先展开为实际自然周集合，再判断集合是否相交。
 * 内部用 long bit mask 表达第 1-63 周，当前默认最大周为 20。
 */
public final class WeekPatternSupport {

    public static final int DEFAULT_START_WEEK = 1;
    public static final int DEFAULT_END_WEEK = 20;
    public static final int DEFAULT_MAX_WEEK = 20;
    public static final int MAX_MASK_WEEK = 63;

    private WeekPatternSupport() {
    }

    public static String normalizeWeekType(String weekType) {
        return WeekTypeSupport.normalize(weekType);
    }

    public static int normalizeStartWeek(Integer startWeek) {
        return startWeek == null ? DEFAULT_START_WEEK : startWeek;
    }

    public static int normalizeEndWeek(Integer endWeek) {
        return endWeek == null ? DEFAULT_END_WEEK : endWeek;
    }

    public static void validateRange(Integer startWeek, Integer endWeek) {
        validateRange(startWeek, endWeek, DEFAULT_MAX_WEEK);
    }

    public static void validateRange(Integer startWeek, Integer endWeek, int maxWeek) {
        int start = normalizeStartWeek(startWeek);
        int end = normalizeEndWeek(endWeek);
        if (maxWeek < DEFAULT_START_WEEK || maxWeek > MAX_MASK_WEEK) {
            throw new IllegalArgumentException("maxWeek must be between 1 and 63");
        }
        if (start < DEFAULT_START_WEEK) {
            throw new IllegalArgumentException("startWeek must be >= 1");
        }
        if (end < start) {
            throw new IllegalArgumentException("endWeek must be >= startWeek");
        }
        if (end > maxWeek) {
            throw new IllegalArgumentException("endWeek must be <= maxWeek");
        }
    }

    public static long activeWeekMask(String weekType, Integer startWeek, Integer endWeek) {
        return activeWeekMask(weekType, startWeek, endWeek, DEFAULT_MAX_WEEK);
    }

    public static long activeWeekMask(String weekType, Integer startWeek, Integer endWeek, int maxWeek) {
        validateRange(startWeek, endWeek, maxWeek);
        String normalizedType = normalizeWeekType(weekType);
        int start = normalizeStartWeek(startWeek);
        int end = normalizeEndWeek(endWeek);
        long mask = 0L;
        for (int week = start; week <= end; week++) {
            if (isActiveInWeek(normalizedType, week)) {
                mask |= 1L << (week - 1);
            }
        }
        return mask;
    }

    public static boolean overlap(String aWeekType, Integer aStartWeek, Integer aEndWeek,
                                  String bWeekType, Integer bStartWeek, Integer bEndWeek) {
        return overlap(aWeekType, aStartWeek, aEndWeek, bWeekType, bStartWeek, bEndWeek, DEFAULT_MAX_WEEK);
    }

    public static boolean overlap(String aWeekType, Integer aStartWeek, Integer aEndWeek,
                                  String bWeekType, Integer bStartWeek, Integer bEndWeek,
                                  int maxWeek) {
        long left = activeWeekMask(aWeekType, aStartWeek, aEndWeek, maxWeek);
        long right = activeWeekMask(bWeekType, bStartWeek, bEndWeek, maxWeek);
        return (left & right) != 0L;
    }

    public static int activeWeekCount(String weekType, Integer startWeek, Integer endWeek) {
        return activeWeekCount(weekType, startWeek, endWeek, DEFAULT_MAX_WEEK);
    }

    public static int activeWeekCount(String weekType, Integer startWeek, Integer endWeek, int maxWeek) {
        return Long.bitCount(activeWeekMask(weekType, startWeek, endWeek, maxWeek));
    }

    /**
     * V10 评分链便捷方法：计算 (weekType, startWeek, endWeek) 的周段签名。
     * 供 {@link com.paike.scheduler.service.scheduling.ScoringFunctions.WeekOwner} 作为第三维 key 使用。
     * 周段相同的 item 签名相同 → 同桶；周段不同的 item 签名不同 → 不同桶。
     * 格式 {@code "startWeek-endWeek"}，默认值归一为 {@code "1-20"}。
     */
    public static String weekRangeKey(String weekType, Integer startWeek, Integer endWeek) {
        int start = normalizeStartWeek(startWeek);
        int end = normalizeEndWeek(endWeek);
        return start + "-" + end;
    }

    public static String displayLabel(String weekType, Integer startWeek, Integer endWeek) {
        return displayLabel(weekType, startWeek, endWeek, DEFAULT_START_WEEK, DEFAULT_END_WEEK);
    }

    public static String displayLabel(String weekType, Integer startWeek, Integer endWeek,
                                      int defaultStartWeek, int defaultEndWeek) {
        String normalizedType = normalizeWeekType(weekType);
        int start = normalizeStartWeek(startWeek);
        int end = normalizeEndWeek(endWeek);
        String typeLabel = WeekTypeSupport.displayLabel(normalizedType);
        boolean defaultRange = start == defaultStartWeek && end == defaultEndWeek;

        if (defaultRange) {
            return typeLabel;
        }
        String rangeLabel = start + "-" + end + "周";
        return typeLabel.isEmpty() ? rangeLabel : rangeLabel + "/" + typeLabel;
    }

    private static boolean isActiveInWeek(String normalizedType, int week) {
        if (WeekTypeSupport.ODD.equals(normalizedType)) {
            return week % 2 == 1;
        }
        if (WeekTypeSupport.EVEN.equals(normalizedType)) {
            return week % 2 == 0;
        }
        return true;
    }
}
