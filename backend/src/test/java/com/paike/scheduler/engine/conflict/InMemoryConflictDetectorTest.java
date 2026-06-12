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

    // Layout:
    //   slot0=(day1,p1), slot1=(day1,p2), slot2=(day2,p1), slot3=(day2,p2)
    //   room0=NORMAL/60, room1=LAB/30, room2=COMPUTER/40
    //   teacher0, teacher1; class0/50人, class1/25人
    //
    //   task0: teacher=0, class=0, NORMAL,  required=2, rooms=[0]
    //   task1: teacher=1, class=1, EXPERIMENT, required=1, rooms=[1]
    //   task2: teacher=0, class=1, COMPUTER, required=1, rooms=[2]
    //   task3: teacher=1, class=0, NORMAL,  required=1, rooms=[0]

    private List<EngineTask> tasks;
    private List<EngineContext.TimeSlotData> timeSlots;
    private List<EngineContext.ClassroomData> classrooms;
    private List<EngineContext.TeacherData> teachers;
    private List<EngineContext.ClassData> classes;
    private List<EngineContext.CourseData> courses;

    @BeforeEach
    void setUp() {
        timeSlots = List.of(
            new EngineContext.TimeSlotData(0, 1L, 1, 1),
            new EngineContext.TimeSlotData(1, 2L, 1, 2),
            new EngineContext.TimeSlotData(2, 3L, 2, 1),
            new EngineContext.TimeSlotData(3, 4L, 2, 2)
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
            new EngineTask(0, 1L, 0, 0, 0, 2, "NORMAL", 50, List.of(0)),
            new EngineTask(1, 2L, 1, 1, 1, 1, "EXPERIMENT", 25, List.of(1)),
            new EngineTask(2, 3L, 0, 1, 2, 1, "COMPUTER", 25, List.of(2)),
            new EngineTask(3, 4L, 1, 0, 0, 1, "NORMAL", 50, List.of(0))
        );
    }

    private EngineContext buildCtx(int teacherMax, int classMax, boolean allowSameCourseSameDay,
                                   boolean[][] unavailable, List<Assignment> locked) {
        return new EngineContext(tasks, timeSlots, classrooms, teachers, classes, courses,
                unavailable, teacherMax, classMax, allowSameCourseSameDay, Map.of(), locked);
    }

    private EngineContext defaultCtx() {
        return buildCtx(3, 4, false, new boolean[2][4], List.of());
    }

    @Test
    void testTeacherUnavailable() {
        boolean[][] unavail = new boolean[2][4];
        unavail[0][0] = true;
        EngineContext ctx = buildCtx(3, 4, false, unavail, List.of());
        InMemoryConflictDetector det = new InMemoryConflictDetector(ctx);
        assertEquals("TEACHER_UNAVAILABLE", det.check(new Assignment(0, 0, 0, 0)));
    }

    @Test
    void testTeacherConflict() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        det.place(new Assignment(0, 0, 0, 0));
        assertEquals("TEACHER_CONFLICT", det.check(new Assignment(2, 0, 0, 2)));
    }

    @Test
    void testClassConflict() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        det.place(new Assignment(0, 0, 0, 0));
        assertEquals("CLASS_CONFLICT", det.check(new Assignment(3, 0, 0, 0)));
    }

    @Test
    void testRoomConflict() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        det.place(new Assignment(0, 0, 0, 0));
        assertEquals("ROOM_CONFLICT", det.check(new Assignment(1, 0, 0, 0)));
    }

    @Test
    void testClassroomCapacityNotEnough() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        assertEquals("CLASSROOM_CAPACITY_NOT_ENOUGH", det.check(new Assignment(0, 0, 0, 1)));
    }

    @Test
    void testRoomTypeMismatchExperiment() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        assertEquals("ROOM_TYPE_MISMATCH", det.check(new Assignment(1, 0, 0, 0)));
    }

    @Test
    void testRoomTypeMismatchComputer() {
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        assertEquals("ROOM_TYPE_MISMATCH", det.check(new Assignment(2, 0, 0, 0)));
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
        assertEquals("TEACHER_DAILY_LIMIT", det.check(new Assignment(2, 0, 1, 2)));
    }

    @Test
    void testClassDailyLimit() {
        EngineContext ctx = buildCtx(3, 1, false, new boolean[2][4], List.of());
        InMemoryConflictDetector det = new InMemoryConflictDetector(ctx);
        det.place(new Assignment(0, 0, 0, 0));
        assertEquals("CLASS_DAILY_LIMIT", det.check(new Assignment(3, 0, 1, 0)));
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
        InMemoryConflictDetector det = new InMemoryConflictDetector(defaultCtx());
        assertNull(det.check(new Assignment(0, 0, 0, 0)));
    }

    @Test
    void testLockedAssignmentPreventsConflict() {
        Assignment locked = new Assignment(0, 0, 0, 0);
        EngineContext ctx = buildCtx(3, 4, false, new boolean[2][4], List.of(locked));
        InMemoryConflictDetector det = new InMemoryConflictDetector(ctx);
        assertEquals("ROOM_CONFLICT", det.check(new Assignment(1, 0, 0, 0)));
    }
}
