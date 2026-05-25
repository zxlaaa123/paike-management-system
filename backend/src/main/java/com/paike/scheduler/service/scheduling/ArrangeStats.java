package com.paike.scheduler.service.scheduling;

/**
 * 一次自动排课跑完后的全局统计，喂给 finalizeBatch 写回 auto_schedule_batch 表。
 */
public record ArrangeStats(
        int generatedCount,
        int successTaskCount,
        int failedTaskCount
) {}
