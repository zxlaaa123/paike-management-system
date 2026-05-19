package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum V5RuleLayerType {
    HARD("HARD"),
    SOFT("SOFT"),
    PREFERENCE("PREFERENCE"),
    REPAIR("REPAIR");

    private final String code;

    V5RuleLayerType(String code) {
        this.code = code;
    }
}

