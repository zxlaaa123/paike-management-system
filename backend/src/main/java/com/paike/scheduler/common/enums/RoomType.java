package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum RoomType {

    NORMAL("NORMAL", "普通教室"),
    MULTIMEDIA("MULTIMEDIA", "多媒体教室"),
    LAB("LAB", "实验室"),
    COMPUTER("COMPUTER", "机房");

    private final String code;
    private final String label;

    RoomType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static RoomType fromCode(String code) {
        for (RoomType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return null;
    }
}
