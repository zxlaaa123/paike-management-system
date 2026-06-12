package com.paike.scheduler.engine.conflict;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;

/**
 * 内存冲突检测器：与 ScheduleConflictService.checkConflict 硬约束语义完全一致。
 * 操作 place/remove/check 均 O(1)。
 */
public class InMemoryConflictDetector {

    private final EngineContext ctx;
    private final int numSlots;
    private final int numTeachers;
    private final int numClasses;
    private final int numRooms;

    private final boolean[][] teacherBusy;
    private final boolean[][] classBusy;
    private final boolean[][] roomBusy;
    private final int[][] teacherDailyCount;
    private final int[][] classDailyCount;
    private final int[] taskScheduledCount;

    public InMemoryConflictDetector(EngineContext ctx) {
        this.ctx = ctx;
        this.numSlots = ctx.timeSlotCount();
        this.numTeachers = ctx.teachers().size();
        this.numClasses = ctx.classes().size();
        this.numRooms = ctx.classrooms().size();

        this.teacherBusy = new boolean[numTeachers][numSlots];
        this.classBusy = new boolean[numClasses][numSlots];
        this.roomBusy = new boolean[numRooms][numSlots];
        this.teacherDailyCount = new int[numTeachers][8];
        this.classDailyCount = new int[numClasses][8];
        this.taskScheduledCount = new int[ctx.taskCount()];

        for (Assignment locked : ctx.lockedAssignments()) {
            place(locked);
        }
    }

    public String check(Assignment a) {
        EngineTask task = ctx.tasks().get(a.taskIndex());
        int teacherIdx = (int) task.teacherIndex();
        int classIdx = (int) task.classIndex();
        int slotIdx = a.timeSlotIndex();
        int roomIdx = a.classroomIndex();

        if (ctx.teacherUnavailable()[teacherIdx][slotIdx]) {
            return "TEACHER_UNAVAILABLE";
        }

        if (teacherBusy[teacherIdx][slotIdx]) {
            return "TEACHER_CONFLICT";
        }

        if (classBusy[classIdx][slotIdx]) {
            return "CLASS_CONFLICT";
        }

        if (roomBusy[roomIdx][slotIdx]) {
            return "ROOM_CONFLICT";
        }

        EngineContext.ClassroomData room = ctx.classrooms().get(roomIdx);
        if (task.studentCount() > 0 && room.capacity() > 0 && room.capacity() < task.studentCount()) {
            return "CLASSROOM_CAPACITY_NOT_ENOUGH";
        }

        if (!isRoomTypeMatched(task.courseType(), room.roomType())) {
            return "ROOM_TYPE_MISMATCH";
        }

        if (taskScheduledCount[a.taskIndex()] + 1 > task.requiredSlots()) {
            return "TASK_NOT_FULLY_SCHEDULED";
        }

        int day = ctx.timeSlots().get(slotIdx).dayOfWeek();
        if (ctx.teacherMaxDailySlots() > 0) {
            if (teacherDailyCount[teacherIdx][day] + 1 > ctx.teacherMaxDailySlots()) {
                return "TEACHER_DAILY_LIMIT";
            }
        }

        if (ctx.classMaxDailySlots() > 0) {
            if (classDailyCount[classIdx][day] + 1 > ctx.classMaxDailySlots()) {
                return "CLASS_DAILY_LIMIT";
            }
        }

        return null;
    }

    public void place(Assignment a) {
        EngineTask task = ctx.tasks().get(a.taskIndex());
        int teacherIdx = (int) task.teacherIndex();
        int classIdx = (int) task.classIndex();
        int slotIdx = a.timeSlotIndex();
        int roomIdx = a.classroomIndex();
        int day = ctx.timeSlots().get(slotIdx).dayOfWeek();

        teacherBusy[teacherIdx][slotIdx] = true;
        classBusy[classIdx][slotIdx] = true;
        roomBusy[roomIdx][slotIdx] = true;
        teacherDailyCount[teacherIdx][day]++;
        classDailyCount[classIdx][day]++;
        taskScheduledCount[a.taskIndex()]++;
    }

    public void remove(Assignment a) {
        EngineTask task = ctx.tasks().get(a.taskIndex());
        int teacherIdx = (int) task.teacherIndex();
        int classIdx = (int) task.classIndex();
        int slotIdx = a.timeSlotIndex();
        int roomIdx = a.classroomIndex();
        int day = ctx.timeSlots().get(slotIdx).dayOfWeek();

        teacherBusy[teacherIdx][slotIdx] = false;
        classBusy[classIdx][slotIdx] = false;
        roomBusy[roomIdx][slotIdx] = false;
        teacherDailyCount[teacherIdx][day]--;
        classDailyCount[classIdx][day]--;
        taskScheduledCount[a.taskIndex()]--;
    }

    private boolean isRoomTypeMatched(String courseType, String roomType) {
        if ("EXPERIMENT".equals(courseType)) {
            return "LAB".equals(roomType);
        }
        if ("COMPUTER".equals(courseType)) {
            return "COMPUTER".equals(roomType);
        }
        return true;
    }
}
