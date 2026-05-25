package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.TimeSlot;

/**
 * 单节排课的尝试结果：要么定位到一个 (slot, room)，要么带着最后一次失败原因返回。
 */
public record AssignmentAttempt(TimeSlot slot, Classroom room, FailReason lastFail) {

    public boolean placed() {
        return slot != null && room != null;
    }

    public static AssignmentAttempt placed(TimeSlot slot, Classroom room) {
        return new AssignmentAttempt(slot, room, FailReason.unknown());
    }

    public static AssignmentAttempt notPlaced(FailReason fail) {
        return new AssignmentAttempt(null, null, fail);
    }
}
