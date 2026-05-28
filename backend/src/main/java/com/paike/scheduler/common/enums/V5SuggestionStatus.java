package com.paike.scheduler.common.enums;

import lombok.Getter;

@Getter
public enum V5SuggestionStatus {
    PENDING("PENDING"),
    ACCEPTED("ACCEPTED"),
    REJECTED("REJECTED"),
    APPLIED("APPLIED"),
    EXPIRED("EXPIRED");

    private final String code;

    V5SuggestionStatus(String code) {
        this.code = code;
    }

    public static V5SuggestionStatus fromCode(String code) {
        for (V5SuggestionStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean is(String code) {
        return this.code.equals(code);
    }
}
