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
}

