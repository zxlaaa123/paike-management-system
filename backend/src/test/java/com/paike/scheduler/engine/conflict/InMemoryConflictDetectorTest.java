package com.paike.scheduler.engine.conflict;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryConflictDetectorTest {

    // 5 tasks:
    //   task0: teacher=0, class=0, course=0, NORMAL,  required=2, rooms=[0]
    //   task1: teacher=1, class=1, course=1, EXPERIMENT, required=1, rooms=[1]
    //   task2: teacher=0, class=1, course=2, COMPUTER, required=1, rooms=[2]
    //   task3: teacher=1, class=1, course=0, NORMAL,  required=1, rooms=[0]
    //   task4: teacher=1, class=0, course=0, NORMAL,  required=1, rooms=[0]

    private List<EngineTask> tasks;
    private List<EngineContext.TimeSlotData> timeSlots;
    private List<EngineContext.ClassroomData> classrooms;
    private List<EngineContext.TeacherData> teachers;
    private List<EngineContext.ClassData> classes;
    private List<EngineContext.CourseData> courses;

    @BeforeEach
    void setUp() {
        timeSlots = List.of(
            new EngineContext.TimeSlotData(0, 1L, 1, 1, "ODD"),
            new EngineContext.TimeSlotData(1, 2L, 1, 2, "ODD"),
            new EngineContext.TimeSlotData(2, 3L, 2, 1, "ODD"),
            new EngineContext.TimeSlotData(3, 4L, 2, 2, "ODD")
        );
        classrooms = List.of(
            new EngineContext.ClassroomData(0, 1L, 60, "NORMAL"),
            new EngineContext.ClassroomData(1, 2L, 30, "LAB"),
            new EngineContext.ClassroomData(2, 3L, 40, "COMPUTER")
        );
        teachers = List.of(
            new EngineContext.TeacherData(0, 1L, "张三", 1),
            new EngineContext.TeacherData(1, 2L, "李四", 1)
        );
        classes = List.of(
            new EngineContext.ClassData(0, 1L, 50, 1),
            new EngineContext.ClassData(1, 2L, 25, 1)
        );
        courses = List.of(
            new EngineContext.CourseData(0, 1L, "NORMAL"),
            new EngineContext.CourseData(1, 2L, "EXPERIMENT"),
            new EngineContext.CourseData(2, 3L, "COMPUTER")
        );
        tasks = List.of(
            new EngineTask(0, 1L, 0, 0, 0, 2, "NORMAL", 50, List.of(0), "ODD", 1, 20),
            new EngineTask(1, 2L, 1, 1, 1, 1, "EXPERIMENT", 25, List.of(1), "ODD", 1, 20),
            new EngineTask(2, 3L, 0, 1, 2, 1, "COMPUTER", 25, List.of(2), "ODD", 1, 20),
            new EngineTask(3, 4L, 1, 1, 0, 1, "NORMAL", 25, List.of(0), "ODD", 1, 20),
            new EngineTask(4, 5L, 1, 0, 0, 1, "NORMAL", 50, List.of(0), "ODD", 1, 20)
        );
    }

    private EngineContext buildCtx(int teacherMax, int classMax, boolean allowSameCourseSameDay,
                                   boolean[][] unavailable, List<Assignment> locked) {
        return new EngineContext(tasks, timeSlots, classrooms, teachers, classes, courses,
                unavailable, new boolean[2], new boolean[2], new boolean[3],
                teacherMax, classMax, allowSameCourseSameDay, 5, Map.of(), locked, List.of(), new int[5]);
    }

    private EngineContext defaultCtx() {
        return buildCtx(3, 4, false, new boolean[2][4], List.of());
    }

    @Test
    void testTeacherUnavailable() {
        boolean[][] unavail = new boolean[2][4];
        unavail[0][0] = true;
        InMemoryConflictDetector det = new InMemoryConflictDetector(buildCtx(3, 4, false, unavail, List.of()));
        assertEquals("TEACHER_UNAVAILABLE", det.check(new Assignment(0, 0, 0, 0)));
    }

    @Test
    void testTeacherDisabled() {
        boolean[] td = {true, false};
        EngineContext ctx = new EngineContext(tasks, timeSlots, classrooms, teachers, classes, courses,
                new boolean[2][4], td, new boolean[2], new boolean[3],
                3, 4, false, 5, Map.of(), List.of(), List.of(), new int[5]);
        assertEquals("TEACHER_DISABLED", new InMemoryConflictDetector(ctx).check(new Assignment(0, 0, 0, 0)));
    }

    @Test
    void testClassDisabled() {
        boolean[] cd = {true, false};
        EngineContext ctx = new EngineContext(tasks, timeSlots, classrooms, teachers, classes, courses,
                new boolean[2][4], new boolean[2], cd, new boolean[3],
                3, 4, false, 5, Map.of(), List.of(), List.of(), new int[5]);
        assertEquals("CLASS_DISABLED", new InMemoryConflictDetector(ctx).check(new Assignment(0, 0, 0, 0)));
    }

    @Test
    void testClassroomDisabled() {
        boolean[] rd = {true, false, false};
        EngineContext ctx = new EngineContext(tasks, timeSlots, classrooms, teachers, classes, courses,
                new boolean[2][4], new boolean[2], new boolean[2], rd,
                3, 4, false, 5, Map.of(), List.of(), List.of(), new int[5]);
        assertEquals("CLASSROOM_DISABLED", new InMemoryConflictDetector(ctx).check(new Assignment(0, 0, 0, 0)));
    }

    @Test
    void testClassroomCapacityNotEnough() {
        // task0 studentCount=50, room1 capacity=30
        assertEquals("CLASSROOM_CAPACITY_NOT_ENOUGH", new InMemoryConflictDetector(defaultCtx()).check(new Assignment(0, 0, 0, 1)));
    }

    @Test
    void testNullStudentCountRejects() {
        List<EngineTask> t = List.of(new EngineTask(0, 1L, 0, 0, 0, 2, "NORMAL", -1, List.of(0), "ODD", 1, 20));
        EngineContext ctx = new EngineContext(t, timeSlots, classrooms, teachers, classes, courses,
                new boolean[2][4], new boolean[2], new boolean[2], new boolean[3],
                3, 4, false, 5, Map.of(), List.of(), List.of(), new int[1]);
        assertEquals("CLASSROOM_CAPACITY_NOT_ENOUGH", new InMemoryConflictDetector(ctx).check(new Assignment(0, 0, 0, 0)));
    }

    @Test
    void testRoomTypeMismatchExperiment() {
        // task1 EXPERIMENT vs room0 NORMAL
        assertEquals("ROOM_TYPE_MISMATCH", new InMemoryConflictDetector(defaultCtx()).check(new Assignment(1, 0, 0, 0)));
    }

    @Test
    void testRoomTypeMismatchComputer() {
        // task2 COMPUTER vs room0 NORMAL
        assertEquals("ROOM_TYPE_MISMATCH", new InMemoryConflictDetector(defaultCtx()).check(new Assignment(2, 0, 0, 0)));
    }

    @Test
    void testTeacherConflict() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        det.place(new Assignment(0, 0, 0, 0));
        // task2 also teacher=0, same slot
        assertEquals("TEACHER_CONFLICT", det.check(new Assignment(2, 0, 0, 2)));
    }

    @Test
    void testClassConflict() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        det.place(new Assignment(0, 0, 0, 0));
        // task4: class=0 (same), teacher=1 (different), same slot, room0
        assertEquals("CLASS_CONFLICT", det.check(new Assignment(4, 0, 0, 0)));
    }

    @Test
    void testRoomConflict() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        det.place(new Assignment(0, 0, 0, 0));
        // task3: class=1, teacher=1, NORMAL, same slot, room0 (occupied)
        assertEquals("ROOM_CONFLICT", det.check(new Assignment(3, 0, 0, 0)));
    }

    @Test
    void testTaskNotFullyScheduled() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        det.place(new Assignment(0, 0, 0, 0));
        det.place(new Assignment(0, 1, 1, 0));
        assertEquals("TASK_NOT_FULLY_SCHEDULED", det.check(new Assignment(0, 2, 2, 0)));
    }

    @Test
    void testTeacherDailyLimit() {
        EngineContext ctx = buildCtx(1, 4, false, new boolean[2][4], List.of());
        InMemoryConflictDetector det = new InMemoryConflictDetector(ctx);
        det.place(new Assignment(0, 0, 0, 0));
        // task2 teacher=0, slot1 (day1) → daily limit
        assertEquals("TEACHER_DAILY_LIMIT", det.check(new Assignment(2, 0, 1, 2)));
    }

    @Test
    void testClassDailyLimit() {
        EngineContext ctx = buildCtx(3, 1, false, new boolean[2][4], List.of());
        InMemoryConflictDetector det = new InMemoryConflictDetector(ctx);
        det.place(new Assignment(0, 0, 0, 0));
        // task4 class=0, slot1 (day1) → daily limit
        assertEquals("CLASS_DAILY_LIMIT", det.check(new Assignment(4, 0, 1, 0)));
    }

    @Test
    void testSameCourseSameDayBlocked() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        det.place(new Assignment(0, 0, 0, 0));
        // task4: class=0, course=0, slot1 (same day1) → SAME_COURSE_SAME_DAY
        assertEquals("SAME_COURSE_SAME_DAY", det.check(new Assignment(4, 0, 1, 0)));
    }

    @Test
    void testSameCourseSameDayAllowed() {
        EngineContext ctx = buildCtx(3, 4, true, new boolean[2][4], List.of());
        InMemoryConflictDetector det = new InMemoryConflictDetector(ctx);
        det.place(new Assignment(0, 0, 0, 0));
        assertNull(det.check(new Assignment(4, 0, 1, 0)));
    }

    @Test
    void testPlaceRemoveSymmetry() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        Assignment a = new Assignment(0, 0, 0, 0);
        det.place(a);
        det.remove(a);
        assertNull(det.check(a));
    }

    @Test
    void testValidAssignment() {
        assertNull(new InMemoryConflictDetector(defaultCtx()).check(new Assignment(0, 0, 0, 0)));
    }

    @Test
    void testLockedAssignmentPreventsConflict() {
        Assignment locked = new Assignment(0, 0, 0, 0);
        EngineContext ctx = buildCtx(3, 4, false, new boolean[2][4], List.of(locked));
        InMemoryConflictDetector det = new InMemoryConflictDetector(ctx);
        // task3: class=1, teacher=1, NORMAL, same slot, room0 (locked)
        assertEquals("ROOM_CONFLICT", det.check(new Assignment(3, 0, 0, 0)));
    }

    @Test
    void testExistingScheduleAsInitialOccupancy() {
        List<Assignment> existing = List.of(new Assignment(0, 0, 0, 0));
        int[] existingCount = {1, 0, 0, 0, 0};
        EngineContext ctx = new EngineContext(tasks, timeSlots, classrooms, teachers, classes, courses,
                new boolean[2][4], new boolean[2], new boolean[2], new boolean[3],
                3, 4, false, 5, Map.of(), List.of(), existing, existingCount);
        InMemoryConflictDetector det = new InMemoryConflictDetector(ctx);
        assertEquals("ROOM_CONFLICT", det.check(new Assignment(3, 0, 0, 0)));
    }
}
