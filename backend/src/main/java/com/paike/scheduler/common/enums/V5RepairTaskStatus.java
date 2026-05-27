package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum V5RepairTaskStatus {
    PENDING("PENDING"),
    CREATED("CREATED"),
    ANALYZING("ANALYZING"),
    SUGGESTED("SUGGESTED"),
    SIMULATED("SIMULATED"),
    APPLIED("APPLIED"),
    CANCELLED("CANCELLED"),
    FAILED("FAILED"),
    ;

    private final String code;

    V5RepairTaskStatus(String code) {
        this.code = code;
    }
}
