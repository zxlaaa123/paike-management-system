package com.paike.scheduler.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulePlanStatusTest {

    @Test
    void keepsPersistedStatusCodesStable() {
        assertEquals("DRAFT", SchedulePlanStatus.DRAFT.getCode());
        assertEquals("APPLIED", SchedulePlanStatus.APPLIED.getCode());
        assertEquals("ABANDONED", SchedulePlanStatus.ABANDONED.getCode());
        assertEquals("SIMULATION", SchedulePlanStatus.SIMULATION.getCode());
        assertEquals("CONFIRMED", SchedulePlanStatus.CONFIRMED.getCode());
        assertEquals("DISCARDED", SchedulePlanStatus.DISCARDED.getCode());
    }

    @Test
    void matchesStatusCodeExactly() {
        assertTrue(SchedulePlanStatus.APPLIED.is("APPLIED"));
        assertFalse(SchedulePlanStatus.APPLIED.is("applied"));
        assertFalse(SchedulePlanStatus.APPLIED.is(null));
    }
}
