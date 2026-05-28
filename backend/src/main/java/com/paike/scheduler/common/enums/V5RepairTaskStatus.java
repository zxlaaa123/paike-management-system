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
    FAILED("FAILED");

    private final String code;

    V5RepairTaskStatus(String code) {
        this.code = code;
    }

    public static V5RepairTaskStatus fromCode(String code) {
        for (V5RepairTaskStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean is(String code) {
        return this.code.equals(code);
    }

    public boolean isTerminal() {
        return this == APPLIED || this == CANCELLED || this == FAILED;
    }
}
