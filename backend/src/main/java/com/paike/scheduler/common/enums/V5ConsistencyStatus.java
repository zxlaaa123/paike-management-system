package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum V5ConsistencyStatus {
    PASS("PASS"),
    WARN("WARN"),
    FAIL("FAIL");

    private final String code;

    V5ConsistencyStatus(String code) {
        this.code = code;
    }
}

