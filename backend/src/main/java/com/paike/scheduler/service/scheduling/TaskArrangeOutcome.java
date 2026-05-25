package com.paike.scheduler.service.scheduling;

/**
 * 单个教学任务排课的全局结果。
 *
 *  - 预检失败（课程/班级缺失、无匹配教室）：generatedThisRun = 0 + lastFail 有码
 *  - 部分成功：generatedThisRun &lt; requiredSlots - alreadyScheduled，lastFail 是最后一次受阻原因
 *  - 全部成功：fullyArranged() == true
 */
public record TaskArrangeOutcome(
        int requiredSlots,
        int alreadyScheduled,
        int generatedThisRun,
        FailReason lastFail
) {

    public boolean fullyArranged() {
        return generatedThisRun >= (requiredSlots - alreadyScheduled);
    }

    public int remainingAfterAttempt() {
        return Math.max(0, requiredSlots - alreadyScheduled - generatedThisRun);
    }

    public static TaskArrangeOutcome preFlightFailed(
            int requiredSlots, int alreadyScheduled, String reasonCode, String reasonMessage) {
        return new TaskArrangeOutcome(
                requiredSlots, alreadyScheduled, 0, new FailReason(reasonCode, reasonMessage));
    }

    public static TaskArrangeOutcome ofProgress(
            int requiredSlots, int alreadyScheduled, int generatedThisRun, FailReason lastFail) {
        return new TaskArrangeOutcome(requiredSlots, alreadyScheduled, generatedThisRun, lastFail);
    }
}
