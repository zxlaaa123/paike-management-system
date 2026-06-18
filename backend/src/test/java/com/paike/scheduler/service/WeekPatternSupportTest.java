package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekPatternSupportTest {

    @ParameterizedTest(name = "overlap({0} {1}-{2}, {3} {4}-{5}) = {6}")
    @CsvSource({
            "ALL,  1,  8, ALL,  9, 16, false",
            "ALL,  1,  8, ODD,  5, 12, true",
            "ODD,  1,  8, EVEN, 1,  8, false",
            "ODD,  1,  8, EVEN, 8, 12, false",
            "ALL,  8,  8, ODD,  1,  9, false",
            "ODD,  1,  9, ODD,  8, 12, true",
            "EVEN, 2,  2, ALL,  1,  3, true",
            "ALL,  1, 20, EVEN, 8, 12, true"
    })
    void overlapUsesActualActiveWeekIntersection(String aType, int aStart, int aEnd,
                                                 String bType, int bStart, int bEnd,
                                                 boolean expected) {
        assertEquals(expected, WeekPatternSupport.overlap(aType, aStart, aEnd, bType, bStart, bEnd));
        assertEquals(expected, WeekPatternSupport.overlap(bType, bStart, bEnd, aType, aStart, aEnd),
                "overlap must be symmetric");
    }

    @Test
    void activeWeekMaskExpandsWeekTypeInsideRange() {
        assertEquals(maskOf(1, 2, 3, 4), WeekPatternSupport.activeWeekMask("ALL", 1, 4));
        assertEquals(maskOf(1, 3, 5, 7), WeekPatternSupport.activeWeekMask("ODD", 1, 8));
        assertEquals(maskOf(8, 10, 12), WeekPatternSupport.activeWeekMask("EVEN", 8, 12));
    }

    @Test
    void activeWeekCountCountsOnlyActualWeeks() {
        assertEquals(8, WeekPatternSupport.activeWeekCount("ALL", 1, 8));
        assertEquals(4, WeekPatternSupport.activeWeekCount("ODD", 1, 8));
        assertEquals(3, WeekPatternSupport.activeWeekCount("EVEN", 8, 12));
        assertEquals(0, WeekPatternSupport.activeWeekCount("ODD", 8, 8));
    }

    @Test
    void nullRangeDefaultsToWholeSemester() {
        assertEquals(maskOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                        11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
                WeekPatternSupport.activeWeekMask(null, null, null));
        assertTrue(WeekPatternSupport.overlap(null, null, null, "EVEN", 8, 12));
    }

    @Test
    void validatesWeekRange() {
        assertThrows(IllegalArgumentException.class, () -> WeekPatternSupport.validateRange(0, 8));
        assertThrows(IllegalArgumentException.class, () -> WeekPatternSupport.validateRange(9, 8));
        assertThrows(IllegalArgumentException.class, () -> WeekPatternSupport.validateRange(1, 21));
        assertThrows(IllegalArgumentException.class, () -> WeekPatternSupport.validateRange(1, 64, 64));
    }

    @Test
    void displayLabelKeepsV9DefaultRangeCompatible() {
        assertEquals("", WeekPatternSupport.displayLabel("ALL", 1, 20));
        assertEquals("单", WeekPatternSupport.displayLabel("ODD", 1, 20));
        assertEquals("双", WeekPatternSupport.displayLabel("EVEN", 1, 20));
        assertEquals("1-8周", WeekPatternSupport.displayLabel("ALL", 1, 8));
        assertEquals("5-12周/单", WeekPatternSupport.displayLabel("ODD", 5, 12));
        assertEquals("8-12周/双", WeekPatternSupport.displayLabel("EVEN", 8, 12));
    }

    @Test
    void supportsCustomMaxWeekForFutureSemesters() {
        assertEquals(maskOf(21), WeekPatternSupport.activeWeekMask("ODD", 21, 21, 24));
        assertFalse(WeekPatternSupport.overlap("ODD", 21, 21, "ALL", 1, 20, 24));
    }

    private static long maskOf(int... weeks) {
        long mask = 0L;
        for (int week : weeks) {
            mask |= 1L << (week - 1);
        }
        return mask;
    }
}
