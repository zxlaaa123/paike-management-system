package com.paike.scheduler.engine.conflict;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V9 阶段3B T9：引擎版冲突检测 weekType 矩阵（方案 X slot 翻倍 + ALL 扩散）。
 *
 * <p>验证引擎在 slot 物理翻倍后正确实现冲突矩阵：
 * <ul>
 *   <li>ODD+EVEN 共槽（不同 slotIdx）不冲突 —— 单双周核心价值</li>
 *   <li>ALL 与 ODD/EVEN 都冲突（ALL 扩散到配对 slot）</li>
 *   <li>同 weekType 同槽冲突</li>
 * </ul>
 *
 * <p>构造翻倍 slot：物理时段 (day=1, period=1) 翻倍为 slot 0(ODD) + slot 1(EVEN)。
 * 裁决依据：V9_00 §5 冲突矩阵、V9_05 T3 引擎版。
 */
class InMemoryConflictDetectorWeekTypeTest {

    private EngineContext buildContext(List<EngineTask> tasks, List<EngineContext.TimeSlotData> slots) {
        return new EngineContext(
                tasks, slots,
                List.of(new EngineContext.ClassroomData(0, 1L, 60, "NORMAL")),
                List.of(new EngineContext.TeacherData(0, 1L, "T1", 1)),
                List.of(new EngineContext.ClassData(0, 1L, 30, 1)),
                List.of(new EngineContext.CourseData(0, 1L, "NORMAL")),
                new boolean[1][slots.size()],    // teacherUnavailable（全 false）
                new boolean[1],                   // teacherDisabled
                new boolean[1],                   // classDisabled
                new boolean[1],                   // classroomDisabled
                0, 0, true,                       // rules（dailyLimit=0 不限，allowSameCourse=true 避免误判）
                5,                                // afternoonStartPeriod
                Map.of(),                         // ruleWeights
                List.of(), List.of(), new int[tasks.size()]);
    }

    /** 翻倍 slot：物理时段 (day=1, period=1) → slot 0(ODD) + slot 1(EVEN) */
    private List<EngineContext.TimeSlotData> doubledSlots() {
        return List.of(
                new EngineContext.TimeSlotData(0, 101L, 1, 1, "ODD"),
                new EngineContext.TimeSlotData(1, 101L, 1, 1, "EVEN"));
    }

    private EngineTask task(int idx, long id, String weekType) {
        return new EngineTask(idx, id, 0, 0, 0, 1, "NORMAL", 30, List.of(0), weekType, 1, 20);
    }

    /** ODD+EVEN 共槽（slot 0 vs slot 1，同物理时段不同周次）不冲突 */
    @Test
    void oddAndEvenSharedSlotNoConflict() {
        // task 0=ODD 占 slot 0，task 1=EVEN 想占 slot 1（同物理时段，不同周次）
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ODD"), task(1, 2L, "EVEN")),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        detector.place(new Assignment(0, 0, 0, 0));  // ODD task 进 slot 0

        // EVEN task 进 slot 1（配对 slot，不同周次）—— 不应冲突
        String conflict = detector.check(new Assignment(1, 0, 1, 0));
        assertNull(conflict, "ODD+EVEN 共槽应不冲突（slot 翻倍隔离），实际: " + conflict);
    }

    /** ALL 与 ODD 冲突（ALL 扩散到配对 slot，占满 ODD+EVEN） */
    @Test
    void allOverlapsWithOdd() {
        // task 0=ALL 占 slot 0（扩散到 slot 1），task 1=ODD 想占 slot 0
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ALL"), task(1, 2L, "ODD")),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        detector.place(new Assignment(0, 0, 0, 0));  // ALL task 进 slot 0（扩散到 slot 1）

        // ODD task 进 slot 0 —— ALL 已扩散到此，应冲突
        String conflict = detector.check(new Assignment(1, 0, 0, 0));
        assertEquals("TEACHER_CONFLICT", conflict, "ALL 与 ODD 同 slot 应冲突");
    }

    /** ALL 与 EVEN 冲突（ALL 扩散到 EVEN slot） */
    @Test
    void allOverlapsWithEven() {
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ALL"), task(1, 2L, "EVEN")),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        detector.place(new Assignment(0, 0, 0, 0));  // ALL 进 slot 0（扩散到 slot 1）

        // EVEN task 进 slot 1 —— ALL 已扩散到此，应冲突
        String conflict = detector.check(new Assignment(1, 0, 1, 0));
        assertEquals("TEACHER_CONFLICT", conflict, "ALL 与 EVEN 配对 slot 应冲突（扩散）");
    }

    /** 同 weekType 同 slot 冲突（ODD+ODD 同 slot） */
    @Test
    void sameWeekTypeSameSlotConflicts() {
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ODD"), task(1, 2L, "ODD")),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        detector.place(new Assignment(0, 0, 0, 0));  // ODD task 0 进 slot 0

        // ODD task 1 进 slot 0 —— 同 teacher 同 slot，应冲突
        String conflict = detector.check(new Assignment(1, 0, 0, 0));
        assertEquals("TEACHER_CONFLICT", conflict, "同 ODD 同 slot 应冲突");
    }

    /** ALL 扩散验证：ALL place 后配对 slot 被占用（remove 后配对 slot 释放） */
    @Test
    void allSpreadsToPairedSlot() {
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ALL"), task(1, 2L, "ODD")),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);

        Assignment allAssignment = new Assignment(0, 0, 0, 0);  // ALL 进 slot 0
        detector.place(allAssignment);

        // slot 1（配对）应被 ALL 扩散占用 —— ODD task 进 slot 1 应冲突
        String conflictAtPaired = detector.check(new Assignment(1, 0, 1, 0));
        assertEquals("TEACHER_CONFLICT", conflictAtPaired, "ALL 应扩散到配对 slot 1");

        // remove ALL 后配对 slot 释放 —— ODD task 进 slot 1 应不冲突
        detector.remove(allAssignment);
        String conflictAfterRemove = detector.check(new Assignment(1, 0, 1, 0));
        assertNull(conflictAfterRemove, "remove ALL 后配对 slot 应释放");
    }
}
