package com.paike.scheduler.engine.conflict;

import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.service.WeekPatternSupport;
import com.paike.scheduler.service.WeekTypeSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存冲突检测器：与 ScheduleConflictService.checkConflict 硬约束语义完全一致。
 * 判定顺序严格对齐 DB 版（per-record iteration: teacher → class → room for each record at same slot）。
 *
 * <p>V9 阶段3 方案 X（slot 物理翻倍）：ODD/EVEN slot 各有独立 slotIdx，占用数组自动隔离。
 * <b>ALL 任务特殊处理</b>：占 ODD+EVEN 两个配对 slot（slotIdx ^ 1），保证 ALL 与 ODD/EVEN 都冲突
 * （全周课占满整个时段）。配对 slot 在同一天，日计数（teacherDailyCount/classDailyCount/classCourseDay）
 * 只算一次（1 个物理大节），taskScheduledCount 也只 +1（pending 按 requiredSlots 计，不按逻辑 slot）。
 */
public class InMemoryConflictDetector {

    private final EngineContext ctx;
    private final int numSlots;
    private final int numTeachers;
    private final int numClasses;
    private final int numRooms;
    private final int numCourses;

    private final boolean[][] teacherBusy;
    private final boolean[][] classBusy;
    private final boolean[][] roomBusy;
    private final int[][] teacherDailyCount;
    private final int[][] classDailyCount;
    private final int[] taskScheduledCount;
    private final int[][][] classCourseDay;
    private final List<List<Assignment>> slotAssignments;

    public InMemoryConflictDetector(EngineContext ctx) {
        this.ctx = ctx;
        this.numSlots = ctx.timeSlotCount();
        this.numTeachers = ctx.teachers().size();
        this.numClasses = ctx.classes().size();
        this.numRooms = ctx.classrooms().size();
        this.numCourses = ctx.courses().size();

        this.teacherBusy = new boolean[numTeachers][numSlots];
        this.classBusy = new boolean[numClasses][numSlots];
        this.roomBusy = new boolean[numRooms][numSlots];
        this.teacherDailyCount = new int[numTeachers][8];
        this.classDailyCount = new int[numClasses][8];
        this.taskScheduledCount = new int[ctx.taskCount()];
        this.classCourseDay = new int[numClasses][numCourses][8];
        this.slotAssignments = new ArrayList<>(numSlots);
        for (int i = 0; i < numSlots; i++) {
            slotAssignments.add(new ArrayList<>());
        }

        for (Assignment existing : ctx.existingScheduleAssignments()) {
            placeInternal(existing);
        }
        for (Assignment locked : ctx.lockedAssignments()) {
            placeInternal(locked);
        }
    }

    public String check(Assignment a) {
        EngineTask task = ctx.tasks().get(a.taskIndex());
        int teacherIdx = (int) task.teacherIndex();
        int classIdx = (int) task.classIndex();
        int slotIdx = a.timeSlotIndex();
        int roomIdx = a.classroomIndex();

        // 1. TEACHER_DISABLED
        if (ctx.teacherDisabled()[teacherIdx]) {
            return "TEACHER_DISABLED";
        }

        // 2. TEACHER_UNAVAILABLE
        if (ctx.teacherUnavailable()[teacherIdx][slotIdx]) {
            return "TEACHER_UNAVAILABLE";
        }

        // 3. CLASS_DISABLED
        if (ctx.classDisabled()[classIdx]) {
            return "CLASS_DISABLED";
        }

        // 4. CLASSROOM_DISABLED
        if (ctx.classroomDisabled()[roomIdx]) {
            return "CLASSROOM_DISABLED";
        }

        // 5. CLASSROOM_CAPACITY_NOT_ENOUGH
        EngineContext.ClassroomData room = ctx.classrooms().get(roomIdx);
        if (task.studentCount() < 0) {
            return "CLASSROOM_CAPACITY_NOT_ENOUGH";
        }
        if (room.capacity() == null || room.capacity() < 0) {
            return "CLASSROOM_CAPACITY_NOT_ENOUGH";
        }
        if (room.capacity() < task.studentCount()) {
            return "CLASSROOM_CAPACITY_NOT_ENOUGH";
        }

        // 6. ROOM_TYPE_MISMATCH
        if (!isRoomTypeMatched(task.courseType(), room.roomType())) {
            return "ROOM_TYPE_MISMATCH";
        }

        // 7-9. Per-record iteration: teacher → class → room (matches DB version)
        // V10 连续周段：实际自然周集合相交才算冲突（ALL 1-8 与 ALL 9-16 共槽合法）
        for (Assignment existing : slotAssignments.get(slotIdx)) {
            EngineTask existingTask = ctx.tasks().get(existing.taskIndex());
            if (existingTask.teacherIndex() == teacherIdx
                    && WeekPatternSupport.overlap(task.weekType(), task.startWeek(), task.endWeek(),
                            existingTask.weekType(), existingTask.startWeek(), existingTask.endWeek())) {
                return "TEACHER_CONFLICT";
            }
            if (existingTask.classIndex() == classIdx
                    && WeekPatternSupport.overlap(task.weekType(), task.startWeek(), task.endWeek(),
                            existingTask.weekType(), existingTask.startWeek(), existingTask.endWeek())) {
                return "CLASS_CONFLICT";
            }
            if (existing.classroomIndex() == roomIdx
                    && WeekPatternSupport.overlap(task.weekType(), task.startWeek(), task.endWeek(),
                            existingTask.weekType(), existingTask.startWeek(), existingTask.endWeek())) {
                return "ROOM_CONFLICT";
            }
        }

        // 10. TASK_NOT_FULLY_SCHEDULED
        if (taskScheduledCount[a.taskIndex()] + 1 > task.requiredSlots()) {
            return "TASK_NOT_FULLY_SCHEDULED";
        }

        // 11. TEACHER_DAILY_LIMIT
        int day = ctx.timeSlots().get(slotIdx).dayOfWeek();
        if (ctx.teacherMaxDailySlots() > 0) {
            if (teacherDailyCount[teacherIdx][day] + 1 > ctx.teacherMaxDailySlots()) {
                return "TEACHER_DAILY_LIMIT";
            }
        }

        // 12. CLASS_DAILY_LIMIT
        if (ctx.classMaxDailySlots() > 0) {
            if (classDailyCount[classIdx][day] + 1 > ctx.classMaxDailySlots()) {
                return "CLASS_DAILY_LIMIT";
            }
        }

        // 13. SAME_COURSE_SAME_DAY
        if (!ctx.allowSameCourseSameDay()) {
            int courseIdx = (int) task.courseIndex();
            if (classCourseDay[classIdx][courseIdx][day] > 0) {
                return "SAME_COURSE_SAME_DAY";
            }
        }

        return null;
    }

    public void place(Assignment a) {
        placeInternal(a);
    }

    /**
     * V9 阶段3：ALL 任务 place 时扩散到配对 slot（slotIdx ^ 1）。
     * 配对 slot 同 day，日计数/classCourseDay/taskScheduledCount 只算一次。
     */
    private void placeInternal(Assignment a) {
        EngineTask task = ctx.tasks().get(a.taskIndex());
        boolean isAll = WeekTypeSupport.ALL.equals(task.weekType());
        placeOneSlot(a, a.timeSlotIndex(), !isAll);
        if (isAll) {
            // ALL 扩散到配对 slot（ODD↔EVEN），但计数已由首 slot 完成，这里只标占用+加列表
            placeOneSlot(a, a.timeSlotIndex() ^ 1, false);
        }
    }

    /**
     * @param countDaily 是否累加 dailyCount/classDailyCount/classCourseDay/taskScheduledCount。
     *                   ALL 的配对 slot 不重复计数。
     */
    private void placeOneSlot(Assignment a, int slotIdx, boolean countDaily) {
        EngineTask task = ctx.tasks().get(a.taskIndex());
        int teacherIdx = (int) task.teacherIndex();
        int classIdx = (int) task.classIndex();
        int roomIdx = a.classroomIndex();
        int day = ctx.timeSlots().get(slotIdx).dayOfWeek();
        int courseIdx = (int) task.courseIndex();

        teacherBusy[teacherIdx][slotIdx] = true;
        classBusy[classIdx][slotIdx] = true;
        roomBusy[roomIdx][slotIdx] = true;
        slotAssignments.get(slotIdx).add(a);
        if (countDaily) {
            teacherDailyCount[teacherIdx][day]++;
            classDailyCount[classIdx][day]++;
            taskScheduledCount[a.taskIndex()]++;
            classCourseDay[classIdx][courseIdx][day]++;
        }
    }

    public void remove(Assignment a) {
        EngineTask task = ctx.tasks().get(a.taskIndex());
        boolean isAll = WeekTypeSupport.ALL.equals(task.weekType());
        removeOneSlot(a, a.timeSlotIndex(), !isAll);
        if (isAll) {
            removeOneSlot(a, a.timeSlotIndex() ^ 1, false);
        }
    }

    private void removeOneSlot(Assignment a, int slotIdx, boolean countDaily) {
        EngineTask task = ctx.tasks().get(a.taskIndex());
        int teacherIdx = (int) task.teacherIndex();
        int classIdx = (int) task.classIndex();
        int roomIdx = a.classroomIndex();
        int day = ctx.timeSlots().get(slotIdx).dayOfWeek();
        int courseIdx = (int) task.courseIndex();

        teacherBusy[teacherIdx][slotIdx] = false;
        classBusy[classIdx][slotIdx] = false;
        roomBusy[roomIdx][slotIdx] = false;
        slotAssignments.get(slotIdx).removeIf(existing ->
            existing.taskIndex() == a.taskIndex()
            && existing.timeSlotIndex() == a.timeSlotIndex()
            && existing.classroomIndex() == a.classroomIndex());
        if (countDaily) {
            teacherDailyCount[teacherIdx][day]--;
            classDailyCount[classIdx][day]--;
            taskScheduledCount[a.taskIndex()]--;
            classCourseDay[classIdx][courseIdx][day]--;
        }
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
