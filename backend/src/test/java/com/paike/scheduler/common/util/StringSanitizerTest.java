package com.paike.scheduler.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringSanitizerTest {

    @Test
    void trimToNullKeepsSharedNormalizationContract() {
        assertNull(StringSanitizer.trimToNull(null));
        assertNull(StringSanitizer.trimToNull("   "));
        assertEquals("数学", StringSanitizer.trimToNull("  数学  "));
    }
}
