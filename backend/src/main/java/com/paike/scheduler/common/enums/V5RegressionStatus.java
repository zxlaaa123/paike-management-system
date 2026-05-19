package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum V5RegressionStatus {
    RUNNING("RUNNING"),
    PASS("PASS"),
    FAIL("FAIL"),
    BLOCKED("BLOCKED");

    private final String code;

    V5RegressionStatus(String code) {
        this.code = code;
    }
}

