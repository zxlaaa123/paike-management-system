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
 * V10 连续周段：引擎版冲突检测周段矩阵。
 *
 * <p>验证 InMemoryConflictDetector 在 slot 物理翻倍模型下，正确处理连续周段：
 * <ul>
 *   <li>ALL 1-8 与 ALL 9-16 共槽（同 slotIdx）不冲突 —— 实际周集合不相交</li>
 *   <li>ALL 1-8 与 ODD 5-12 共槽冲突 —— 重叠自然周 5、7</li>
 *   <li>ODD 1-8 与 EVEN 8-12 共槽不冲突 —— V9 单双周语义 + 周段不相交</li>
 *   <li>ODD 1-9 与 ODD 8-12 共槽冲突 —— 重叠自然周 9</li>
 * </ul>
 *
 * <p>裁决依据：V10_00 §3.2 冲突语义、V10_02 阶段 4 关键裁决。
 */
class InMemoryConflictDetectorWeekRangeTest {

    private EngineContext buildContext(List<EngineTask> tasks, List<EngineContext.TimeSlotData> slots) {
        return new EngineContext(
                tasks, slots,
                List.of(new EngineContext.ClassroomData(0, 1L, 60, "NORMAL")),
                List.of(new EngineContext.TeacherData(0, 1L, "T1", 1)),
                List.of(new EngineContext.ClassData(0, 1L, 30, 1)),
                List.of(new EngineContext.CourseData(0, 1L, "NORMAL")),
                new boolean[1][slots.size()],
                new boolean[1],
                new boolean[1],
                new boolean[1],
                0, 0, true,
                5,
                Map.of(),
                List.of(), List.of(), new int[tasks.size()]);
    }

    /** 翻倍 slot：物理时段 (day=1, period=1) → slot 0(ODD) + slot 1(EVEN) */
    private List<EngineContext.TimeSlotData> doubledSlots() {
        return List.of(
                new EngineContext.TimeSlotData(0, 101L, 1, 1, "ODD"),
                new EngineContext.TimeSlotData(1, 101L, 1, 1, "EVEN"));
    }

    private EngineTask task(int idx, long id, String weekType, int startWeek, int endWeek) {
        return new EngineTask(idx, id, 0, 0, 0, 1, "NORMAL", 30, List.of(0), weekType, startWeek, endWeek);
    }

    /** ALL 1-8 与 ALL 9-16 同 slot（同教师/班级/教室）→ 实际周集合不相交 → 不冲突 */
    @Test
    void allDisjointWeekRangeNoConflict() {
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ALL", 1, 8), task(1, 2L, "ALL", 9, 16)),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        // ALL task 0 进 slot 0（扩散到 slot 1）
        detector.place(new Assignment(0, 0, 0, 0));

        // ALL task 1 进 slot 0 —— 虽然 ALL 扩散占了 slot 0，但周集合不相交，不应冲突
        String conflict = detector.check(new Assignment(1, 0, 0, 0));
        assertNull(conflict, "ALL 1-8 与 ALL 9-16 实际周集合不相交，应不冲突，实际: " + conflict);
    }

    /** ALL 1-8 与 ODD 5-12 同 slot → 重叠自然周 5、7 → 冲突 */
    @Test
    void allOverlappingWithOddConflicts() {
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ALL", 1, 8), task(1, 2L, "ODD", 5, 12)),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        detector.place(new Assignment(0, 0, 0, 0));  // ALL 1-8 进 slot 0（扩散到 slot 1）

        // ODD 5-12 进 slot 0 —— ALL 已占，且周集合相交（5、7），应冲突
        String conflict = detector.check(new Assignment(1, 0, 0, 0));
        assertEquals("TEACHER_CONFLICT", conflict, "ALL 1-8 与 ODD 5-12 重叠自然周 5、7，应冲突");
    }

    /** ODD 1-8 与 EVEN 8-12 配对 slot → V9 单双周不冲突 + 周段第8周不相交 → 不冲突 */
    @Test
    void oddEvenDisjointWeekRangeNoConflict() {
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ODD", 1, 8), task(1, 2L, "EVEN", 8, 12)),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        detector.place(new Assignment(0, 0, 0, 0));  // ODD 1-8 进 slot 0

        // EVEN 8-12 进 slot 1（配对 slot）—— 单双周隔离 + 第8周是偶周但 ODD 1-8 不含第8周
        String conflict = detector.check(new Assignment(1, 0, 1, 0));
        assertNull(conflict, "ODD 1-8 与 EVEN 8-12 实际周集合不相交，应不冲突，实际: " + conflict);
    }

    /** ODD 1-9 与 ODD 8-12 同 slot → 重叠自然周 9 → 冲突 */
    @Test
    void oddOverlappingWithOddConflicts() {
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ODD", 1, 9), task(1, 2L, "ODD", 8, 12)),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        detector.place(new Assignment(0, 0, 0, 0));  // ODD 1-9 进 slot 0

        // ODD 8-12 进 slot 0 —— 同 ODD 同 slot，且周集合相交（第9周），应冲突
        String conflict = detector.check(new Assignment(1, 0, 0, 0));
        assertEquals("TEACHER_CONFLICT", conflict, "ODD 1-9 与 ODD 8-12 重叠自然周 9，应冲突");
    }

    /** ALL 1-8 扩散到配对 slot 后，ODD 9-16 进配对 slot → 周集合不相交 → 不冲突 */
    @Test
    void allSpreadToPairedSlotDisjointNoConflict() {
        EngineContext ctx = buildContext(
                List.of(task(0, 1L, "ALL", 1, 8), task(1, 2L, "ODD", 9, 16)),
                doubledSlots());
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        // ALL 1-8 进 slot 0（扩散到 slot 1）
        detector.place(new Assignment(0, 0, 0, 0));

        // ODD 9-16 进 slot 1（配对 slot，ALL 已扩散到此）
        // 虽然扩散占了 slot 1，但 ALL 1-8 与 ODD 9-16 周集合不相交，不应冲突
        String conflict = detector.check(new Assignment(1, 0, 1, 0));
        assertNull(conflict, "ALL 1-8 扩散到配对 slot，但与 ODD 9-16 周集合不相交，应不冲突，实际: " + conflict);
    }
}
