package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum SchedulePlanStatus {
    DRAFT("DRAFT"),
    APPLIED("APPLIED"),
    ABANDONED("ABANDONED"),
    SIMULATION("SIMULATION"),
    CONFIRMED("CONFIRMED"),
    DISCARDED("DISCARDED");

    private final String code;

    SchedulePlanStatus(String code) {
        this.code = code;
    }

    public boolean is(String status) {
        return code.equals(status);
    }
}
