package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum ScheduleSourceType {

    MANUAL("MANUAL", "手动排课"),
    AUTO("AUTO", "自动排课");

    private final String code;
    private final String label;

    ScheduleSourceType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ScheduleSourceType fromCode(String code) {
        for (ScheduleSourceType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return null;
    }
}
