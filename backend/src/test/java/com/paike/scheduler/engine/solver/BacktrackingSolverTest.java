package com.paike.scheduler.engine.solver;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineSolution;
import com.paike.scheduler.engine.model.EngineTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BacktrackingSolverTest {

    @Test
    void solvesGreedyTrapByBacktracking() {
        BacktrackingSolver solver = new BacktrackingSolver(greedyTrapContext(), SolverConfig.withSeed(1L));

        EngineSolution solution = solver.solve();

        assertEquals(2, solution.assignments().size());
        assertTrue(solution.unassignedSlots().isEmpty());
        assertTrue(solver.backtracksUsed() > 0);
        assertAssignment(solution, 0, 2, 0);
        assertAssignment(solution, 1, 0, 0);
    }

    @Test
    void reportsPartialWhenNoCandidateExists() {
        List<EngineTask> tasks = List.of(new EngineTask(0, 101L, 0, 0, 0, 1, "NORMAL", 30, List.of(0)));
        boolean[][] unavailable = new boolean[1][1];
        unavailable[0][0] = true;
        EngineContext ctx = context(tasks, oneSlot(), oneRoom(), oneTeacher(), oneClass(), oneCourse(),
                unavailable, List.of(), List.of(), new int[tasks.size()]);

        EngineSolution solution = new BacktrackingSolver(ctx, SolverConfig.withSeed(1L)).solve();

        assertTrue(solution.assignments().isEmpty());
        assertEquals(1, solution.unassignedSlots().size());
        assertEquals("NO_AVAILABLE_SLOT", solution.unassignedSlots().get(0).reasonType());
    }

    @Test
    void budgetExceededFallsBackToPartialWithoutHanging() {
        SolverConfig config = new SolverConfig(1L, 0, 5_000, true);
        BacktrackingSolver solver = new BacktrackingSolver(greedyTrapContext(), config);

        EngineSolution solution = solver.solve();

        assertEquals(1, solution.assignments().size());
        assertEquals(1, solution.unassignedSlots().size());
    }

    @Test
    void lockedAssignmentsAreNotMoved() {
        List<EngineTask> tasks = List.of(
                new EngineTask(0, 101L, 0, 0, 0, 1, "NORMAL", 30, List.of(0)),
                new EngineTask(1, 102L, 1, 1, 1, 1, "NORMAL", 30, List.of(0))
        );
        List<EngineContext.TimeSlotData> slots = List.of(
                new EngineContext.TimeSlotData(0, 201L, 1, 1),
                new EngineContext.TimeSlotData(1, 202L, 2, 1)
        );
        EngineContext ctx = context(tasks, slots, oneRoom(), twoTeachers(), twoClasses(), twoCourses(),
                new boolean[2][2], List.of(new Assignment(0, 0, 0, 0)), List.of(), new int[tasks.size()]);

        EngineSolution solution = new BacktrackingSolver(ctx, SolverConfig.withSeed(1L)).solve();

        assertEquals(1, solution.assignments().size());
        assertTrue(solution.assignments().stream().noneMatch(a -> a.taskIndex() == 0));
        assertAssignment(solution, 1, 1, 0);
    }

    private static EngineContext greedyTrapContext() {
        List<EngineTask> tasks = List.of(
                new EngineTask(0, 101L, 0, 0, 0, 1, "NORMAL", 30, List.of(0)),
                new EngineTask(1, 102L, 1, 0, 0, 1, "NORMAL", 30, List.of(0))
        );
        List<EngineContext.TimeSlotData> slots = List.of(
                new EngineContext.TimeSlotData(0, 201L, 1, 1),
                new EngineContext.TimeSlotData(1, 202L, 1, 2),
                new EngineContext.TimeSlotData(2, 203L, 2, 1),
                new EngineContext.TimeSlotData(3, 204L, 2, 2)
        );
        boolean[][] unavailable = new boolean[2][4];
        unavailable[0][1] = true;
        unavailable[0][3] = true;
        unavailable[1][2] = true;
        unavailable[1][3] = true;
        return context(tasks, slots, oneRoom(), twoTeachers(), oneClass(), oneCourse(),
                unavailable, List.of(), List.of(), new int[tasks.size()]);
    }

    private static void assertAssignment(EngineSolution solution, int taskIndex, int timeSlotIndex, int classroomIndex) {
        assertTrue(solution.assignments().stream().anyMatch(a ->
                a.taskIndex() == taskIndex
                        && a.timeSlotIndex() == timeSlotIndex
                        && a.classroomIndex() == classroomIndex));
    }

    private static EngineContext context(
            List<EngineTask> tasks,
            List<EngineContext.TimeSlotData> slots,
            List<EngineContext.ClassroomData> rooms,
            List<EngineContext.TeacherData> teachers,
            List<EngineContext.ClassData> classes,
            List<EngineContext.CourseData> courses,
            boolean[][] unavailable,
            List<Assignment> locked,
            List<Assignment> existing,
            int[] existingCount
    ) {
        return new EngineContext(tasks, slots, rooms, teachers, classes, courses,
                unavailable, new boolean[teachers.size()], new boolean[classes.size()], new boolean[rooms.size()],
                4, 4, false, 5, Map.of(), locked, existing, existingCount);
    }

    private static List<EngineContext.TimeSlotData> oneSlot() {
        return List.of(new EngineContext.TimeSlotData(0, 201L, 1, 1));
    }

    private static List<EngineContext.ClassroomData> oneRoom() {
        return List.of(new EngineContext.ClassroomData(0, 301L, 60, "NORMAL"));
    }

    private static List<EngineContext.TeacherData> oneTeacher() {
        return List.of(new EngineContext.TeacherData(0, 401L, "T1", 1));
    }

    private static List<EngineContext.TeacherData> twoTeachers() {
        return List.of(
                new EngineContext.TeacherData(0, 401L, "T1", 1),
                new EngineContext.TeacherData(1, 402L, "T2", 1)
        );
    }

    private static List<EngineContext.ClassData> oneClass() {
        return List.of(new EngineContext.ClassData(0, 501L, 30, 1));
    }

    private static List<EngineContext.ClassData> twoClasses() {
        return List.of(
                new EngineContext.ClassData(0, 501L, 30, 1),
                new EngineContext.ClassData(1, 502L, 30, 1)
        );
    }

    private static List<EngineContext.CourseData> oneCourse() {
        return List.of(new EngineContext.CourseData(0, 601L, "NORMAL"));
    }

    private static List<EngineContext.CourseData> twoCourses() {
        return List.of(
                new EngineContext.CourseData(0, 601L, "NORMAL"),
                new EngineContext.CourseData(1, 602L, "NORMAL")
        );
    }
}
