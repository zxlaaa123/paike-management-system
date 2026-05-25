package com.paike.scheduler.service.scheduling;

/**
 * 单次排课尝试失败时记录的原因码 + 人类可读信息，
 * 最终写入 schedule_unassigned_task 表。
 */
public record FailReason(String code, String message) {

    public static FailReason unknown() {
        return new FailReason("UNKNOWN", "");
    }
}
