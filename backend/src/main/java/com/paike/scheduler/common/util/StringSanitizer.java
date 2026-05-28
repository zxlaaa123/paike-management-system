package com.paike.scheduler.common.util;

public final class StringSanitizer {

    private StringSanitizer() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
