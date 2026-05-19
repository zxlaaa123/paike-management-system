package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum V5RepairTaskStatus {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    GENERATED("GENERATED"),
    APPLIED("APPLIED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String code;

    V5RepairTaskStatus(String code) {
        this.code = code;
    }
}

