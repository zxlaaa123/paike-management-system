package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum CourseType {

    NORMAL("NORMAL", "普通课"),
    EXPERIMENT("EXPERIMENT", "实验课"),
    COMPUTER("COMPUTER", "机房课"),
    PE("PE", "体育课");

    private final String code;
    private final String label;

    CourseType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static CourseType fromCode(String code) {
        for (CourseType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return null;
    }
}
