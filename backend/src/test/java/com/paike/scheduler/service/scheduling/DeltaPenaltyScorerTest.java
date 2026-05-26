package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.entity.SchedulePlanItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeltaPenaltyScorerTest {

    private static final int AFTERNOON_START_PERIOD = 5;

    @Test
    void deltaPenalty_matchesOfflinePenaltyDifferenceForEachSoftRule() {
        List<SchedulePlanItem> current = List.of(
                item(1L, 1L, 1L, 1L, 1, 1),
                item(1L, 1L, 1L, 1L, 1, 3),
                item(2L, 2L, 2L, 2L, 2, 5),
                item(2L, 2L, 2L, 2L, 2, 7)
        );
        SchedulePlanItem candidate = item(1L, 1L, 1L, 2L, 2, 1);

        assertDelta("0.0313", DeltaPenaltyScorer.CLASS_DAILY_BALANCE, current, candidate);
        assertDelta("0.0313", DeltaPenaltyScorer.TEACHER_DAILY_LOAD, current, candidate);
        assertDelta("-0.3333", DeltaPenaltyScorer.COURSE_DISTRIBUTION, current, candidate);
        assertDelta("-0.1667", DeltaPenaltyScorer.CONTINUOUS_PERIOD_LIMIT, current, candidate);
        assertDelta("0.0400", DeltaPenaltyScorer.CLASSROOM_UTILIZATION, current, candidate);
        assertDelta("-0.1000", DeltaPenaltyScorer.MORNING_THEORY_PRIORITY, current, candidate);
    }

    @Test
    void weightedSoftDeltaPenalty_sumsOnlyConfiguredWeights() {
        List<SchedulePlanItem> current = List.of(
                item(1L, 1L, 1L, 1L, 1, 1),
                item(1L, 1L, 1L, 1L, 1, 3),
                item(2L, 2L, 2L, 2L, 2, 5),
                item(2L, 2L, 2L, 2L, 2, 7)
        );
        SchedulePlanItem candidate = item(1L, 1L, 1L, 2L, 2, 1);
        Map<String, BigDecimal> weights = Map.of(
                DeltaPenaltyScorer.CLASS_DAILY_BALANCE, new BigDecimal("30"),
                DeltaPenaltyScorer.TEACHER_DAILY_LOAD, new BigDecimal("30"),
                DeltaPenaltyScorer.COURSE_DISTRIBUTION, new BigDecimal("25"),
                DeltaPenaltyScorer.CONTINUOUS_PERIOD_LIMIT, new BigDecimal("25"),
                DeltaPenaltyScorer.CLASSROOM_UTILIZATION, new BigDecimal("20"),
                DeltaPenaltyScorer.MORNING_THEORY_PRIORITY, new BigDecimal("20")
        );

        BigDecimal delta = DeltaPenaltyScorer.weightedSoftDeltaPenalty(
                weights,
                current,
                candidate,
                AFTERNOON_START_PERIOD);

        assertEquals(new BigDecimal("-11.8220"), delta);
    }

    @Test
    void deltaPenalty_returnsZeroForUnsupportedRuleCode() {
        BigDecimal delta = DeltaPenaltyScorer.deltaPenalty(
                "UNSUPPORTED",
                List.of(),
                item(1L, 1L, 1L, 1L, 1, 1),
                AFTERNOON_START_PERIOD);

        assertEquals(BigDecimal.ZERO, delta);
    }

    private void assertDelta(
            String expected,
            String ruleCode,
            List<SchedulePlanItem> current,
            SchedulePlanItem candidate
    ) {
        assertEquals(
                new BigDecimal(expected),
                DeltaPenaltyScorer.deltaPenalty(ruleCode, current, candidate, AFTERNOON_START_PERIOD),
                ruleCode);
    }

    private static SchedulePlanItem item(
            Long teacherId,
            Long classId,
            Long courseId,
            Long classroomId,
            int weekday,
            int startPeriod
    ) {
        SchedulePlanItem item = new SchedulePlanItem();
        item.setTeacherId(teacherId);
        item.setClassId(classId);
        item.setCourseId(courseId);
        item.setClassroomId(classroomId);
        item.setWeekday(weekday);
        item.setStartPeriod(startPeriod);
        return item;
    }
}

