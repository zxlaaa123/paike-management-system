package com.paike.scheduler.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V5StatusEnumConversionTest {

    @Test
    void repairTaskStatus_convertsByCodeAndDetectsTerminalStates() {
        assertEquals(V5RepairTaskStatus.SUGGESTED, V5RepairTaskStatus.fromCode("SUGGESTED"));
        assertNull(V5RepairTaskStatus.fromCode("suggested"));
        assertTrue(V5RepairTaskStatus.APPLIED.is("APPLIED"));
        assertTrue(V5RepairTaskStatus.APPLIED.isTerminal());
        assertTrue(V5RepairTaskStatus.CANCELLED.isTerminal());
        assertTrue(V5RepairTaskStatus.FAILED.isTerminal());
        assertFalse(V5RepairTaskStatus.SIMULATED.isTerminal());
    }

    @Test
    void suggestionStatus_convertsByCode() {
        assertEquals(V5SuggestionStatus.ACCEPTED, V5SuggestionStatus.fromCode("ACCEPTED"));
        assertNull(V5SuggestionStatus.fromCode("accepted"));
        assertTrue(V5SuggestionStatus.PENDING.is("PENDING"));
        assertFalse(V5SuggestionStatus.PENDING.is(null));
    }
}
