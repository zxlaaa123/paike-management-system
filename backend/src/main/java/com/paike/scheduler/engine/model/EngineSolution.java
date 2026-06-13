package com.paike.scheduler.engine.model;

import java.util.List;

/**
 * 求解结果：一组分配 + 未排任务列表。
 */
public record EngineSolution(
    List<Assignment> assignments,
    List<UnassignedSlot> unassignedSlots,
    SolverStats stats
) {
    /**
     * 未排的大节。
     * @param taskIndex 任务索引
     * @param slotIndex 该任务的第几个大节
     * @param reasonType 失败原因 TYPE 标签
     */
    public record UnassignedSlot(int taskIndex, int slotIndex, String reasonType) {}

    public record SolverStats(
            int backtracks,
            int annealingSteps,
            double initialScore,
            double finalScore
    ) {
        public static SolverStats feasibleOnly(int backtracks) {
            return new SolverStats(backtracks, 0, 0D, 0D);
        }
    }
}
