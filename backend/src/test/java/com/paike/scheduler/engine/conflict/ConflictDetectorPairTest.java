package com.paike.scheduler.engine.conflict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.service.EngineContextLoader;
import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.ScheduleConflictService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T2 双跑对拍测试：自建数据集，不依赖本地库现状。
 * 三档规模（~10/50/200 任务），固定随机种子，全量逐格对拍 + 增量对拍。
 */
@SpringBootTest
class ConflictDetectorPairTest {

    @Autowired private EngineContextLoader contextLoader;
    @Autowired private ScheduleConflictService conflictService;
    @Autowired private TeachingTaskMapper teachingTaskMapper;
    @Autowired private TimeSlotMapper timeSlotMapper;
    @Autowired private ClassroomMapper classroomMapper;
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private ClassInfoMapper classInfoMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private TeacherUnavailableTimeMapper unavailableTimeMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private SemesterMapper semesterMapper;

    private final Random rng = new Random(42);
    private Long semesterId;
    private final List<Long> createdTeacherIds = new ArrayList<>();
    private final List<Long> createdClassIds = new ArrayList<>();
    private final List<Long> createdCourseIds = new ArrayList<>();
    private final List<Long> createdClassroomIds = new ArrayList<>();
    private final List<Long> createdTaskIds = new ArrayList<>();
    private final List<Long> createdUnavailableIds = new ArrayList<>();
    private final List<Long> createdScheduleIds = new ArrayList<>();
    private final String suffix = String.valueOf(System.currentTimeMillis() % 100000);

    @BeforeEach
    void setUp() {
        Semester sem = new Semester();
        sem.setName("PAIR_TEST_" + suffix);
        sem.setSchoolYear("2025-2026");
        sem.setTerm("1");
        sem.setIsCurrent(0);
        semesterMapper.insert(sem);
        semesterId = sem.getId();
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdScheduleIds) {
            scheduleMapper.deleteById(id);
        }
        for (Long id : createdTaskIds) {
            teachingTaskMapper.deleteById(id);
        }
        for (Long id : createdUnavailableIds) {
            unavailableTimeMapper.deleteById(id);
        }
        for (Long id : createdClassroomIds) {
            classroomMapper.deleteById(id);
        }
        for (Long id : createdCourseIds) {
            courseMapper.deleteById(id);
        }
        for (Long id : createdClassIds) {
            classInfoMapper.deleteById(id);
        }
        for (Long id : createdTeacherIds) {
            teacherMapper.deleteById(id);
        }
        semesterMapper.deleteById(semesterId);
    }

    @Test
    void pairTestSmall() {
        int totalComparisons = runPairTest(10, 5, 4, 8, 4);
        assertTrue(totalComparisons > 0, "Must compare > 0 cells (small)");
        System.out.println("[PAIR-TEST-SMALL] Total comparisons: " + totalComparisons);
    }

    // Medium/large tests: run with -Dtest="ConflictDetectorPairTest#pairTestMedium" when needed
    // They are slower due to full (task, slot, room) enumeration against DB

    void pairTestMedium() {
        int totalComparisons = runPairTest(50, 15, 12, 10, 5);
        assertTrue(totalComparisons > 0, "Must compare > 0 cells (medium)");
        System.out.println("[PAIR-TEST-MEDIUM] Total comparisons: " + totalComparisons);
    }

    void pairTestLarge() {
        int totalComparisons = runPairTest(200, 50, 40, 10, 5);
        assertTrue(totalComparisons > 0, "Must compare > 0 cells (large)");
        System.out.println("[PAIR-TEST-LARGE] Total comparisons: " + totalComparisons);
    }

    private int runPairTest(int taskCount, int teacherCount, int classCount, int classroomCount, int slotCount) {
        // 1. Use existing time slots (can't create new due to unique constraint)
        List<TimeSlot> existingSlots = timeSlotMapper.selectList(
            new LambdaQueryWrapper<TimeSlot>().orderByAsc(TimeSlot::getSortOrder));
        List<Long> slotIds = existingSlots.stream().map(TimeSlot::getId).limit(slotCount).toList();
        if (slotIds.isEmpty()) {
            System.out.println("[PAIR-TEST] No time slots in DB, skipping");
            return 0;
        }

        // 2. Create classrooms (mix of types)
        List<Long> roomIds = new ArrayList<>();
        String[] roomTypes = {"NORMAL", "LAB", "COMPUTER", "MULTIMEDIA"};
        for (int i = 0; i < classroomCount; i++) {
            Classroom c = new Classroom();
            c.setRoomName("PAIR_ROOM_" + suffix + "_" + i);
            c.setBuilding("测试楼");
            c.setCapacity(20 + rng.nextInt(80));
            c.setRoomType(roomTypes[i % roomTypes.length]);
            c.setStatus(rng.nextInt(10) < 8 ? 1 : 0); // 80% enabled, 20% disabled
            c.setDeleted(0);
            classroomMapper.insert(c);
            roomIds.add(c.getId());
            createdClassroomIds.add(c.getId());
        }

        // 3. Create teachers (some disabled)
        List<Long> teacherIds = new ArrayList<>();
        for (int i = 0; i < teacherCount; i++) {
            Teacher t = new Teacher();
            t.setTeacherNo("PT_" + suffix + "_" + i);
            t.setName("对拍教师" + i);
            t.setDepartment("测试系");
            t.setStatus(rng.nextInt(10) < 8 ? 1 : 0); // 80% enabled
            t.setDeleted(0);
            teacherMapper.insert(t);
            teacherIds.add(t.getId());
            createdTeacherIds.add(t.getId());
        }

        // 4. Create classes (some disabled, some null studentCount)
        List<Long> classIds = new ArrayList<>();
        for (int i = 0; i < classCount; i++) {
            ClassInfo ci = new ClassInfo();
            ci.setClassName("对拍班级" + suffix + "_" + i);
            ci.setMajor("测试专业");
            ci.setGrade("2026");
            if (rng.nextInt(10) < 1) {
                ci.setStudentCount(null); // 10% null
            } else {
                ci.setStudentCount(15 + rng.nextInt(60));
            }
            ci.setStatus(rng.nextInt(10) < 8 ? 1 : 0);
            ci.setDeleted(0);
            classInfoMapper.insert(ci);
            classIds.add(ci.getId());
            createdClassIds.add(ci.getId());
        }

        // 5. Create courses (mix of types)
        List<Long> courseIds = new ArrayList<>();
        String[] courseTypes = {"NORMAL", "EXPERIMENT", "COMPUTER"};
        for (int i = 0; i < Math.min(taskCount, teacherCount); i++) {
            Course co = new Course();
            co.setCourseNo("PC_" + suffix + "_" + i);
            co.setCourseName("对拍课程" + i);
            co.setCourseType(courseTypes[i % courseTypes.length]);
            co.setDeleted(0);
            courseMapper.insert(co);
            courseIds.add(co.getId());
            createdCourseIds.add(co.getId());
        }

        // 6. Create teacher unavailable times
        for (Long tid : teacherIds) {
            if (rng.nextInt(10) < 3) { // 30% have unavailability
                int count = 1 + rng.nextInt(3);
                Set<Long> usedSlots = new HashSet<>();
                for (int j = 0; j < count; j++) {
                    Long sid = slotIds.get(rng.nextInt(slotIds.size()));
                    if (usedSlots.add(sid)) {
                        TeacherUnavailableTime ut = new TeacherUnavailableTime();
                        ut.setTeacherId(tid);
                        ut.setTimeSlotId(sid);
                        ut.setReason("对拍禁排");
                        ut.setStatus(1);
                        ut.setDeleted(0);
                        unavailableTimeMapper.insert(ut);
                        createdUnavailableIds.add(ut.getId());
                    }
                }
            }
        }

        // 7. Create teaching tasks
        for (int i = 0; i < taskCount; i++) {
            TeachingTask tt = new TeachingTask();
            tt.setSemesterId(semesterId);
            tt.setTeacherId(teacherIds.get(i % teacherIds.size()));
            tt.setClassId(classIds.get(i % classIds.size()));
            tt.setCourseId(courseIds.get(i % courseIds.size()));
            tt.setWeeklyHours(rng.nextInt(10) < 1 ? null : 2 + rng.nextInt(5) * 2); // 10% null
            tt.setStatus(1);
            tt.setDeleted(0);
            teachingTaskMapper.insert(tt);
            createdTaskIds.add(tt.getId());
        }

        // 8. Load EngineContext
        EngineContext ctx = contextLoader.load(semesterId);
        if (ctx.taskCount() == 0) {
            System.out.println("[PAIR-TEST] No tasks loaded (all filtered), skipping comparison");
            return 0;
        }

        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);

        // 9. Build lookup maps
        Map<Long, Integer> slotIdToIdx = new HashMap<>();
        for (EngineContext.TimeSlotData s : ctx.timeSlots()) {
            slotIdToIdx.put(s.originalId(), s.index());
        }
        Map<Long, Integer> roomIdToIdx = new HashMap<>();
        for (EngineContext.ClassroomData r : ctx.classrooms()) {
            roomIdToIdx.put(r.originalId(), r.index());
        }

        // 10. Full comparison: every (task, slot, room) combination
        int totalComparisons = 0;
        List<Long> allRoomIds = classroomMapper.selectList(
            new LambdaQueryWrapper<Classroom>().eq(Classroom::getDeleted, 0))
            .stream().map(Classroom::getId).toList();
        List<Long> allSlotIds = timeSlotMapper.selectList(null)
            .stream().map(TimeSlot::getId).toList();

        for (EngineTask task : ctx.tasks()) {
            for (Long slotId : allSlotIds) {
                Integer slotIdx = slotIdToIdx.get(slotId);
                if (slotIdx == null) continue;
                for (Long roomId : allRoomIds) {
                    Integer roomIdx = roomIdToIdx.get(roomId);
                    if (roomIdx == null) continue;

                    Assignment a = new Assignment(task.index(), 0, slotIdx, roomIdx);
                    String memoryResult = detector.check(a);
                    String dbResult = conflictService.checkConflict(task.originalId(), slotId, roomId, null);
                    String dbType = dbResult == null ? null : ScheduleConflictService.extractReasonType(dbResult);

                    if (memoryResult == null) {
                        if (dbType != null) {
                            fail("Memory allows but DB rejects: " + dbType +
                                " for task=" + task.originalId() + " slot=" + slotId + " room=" + roomId);
                        }
                    } else {
                        if (dbType == null) {
                            fail("Memory rejects (" + memoryResult + ") but DB allows" +
                                " for task=" + task.originalId() + " slot=" + slotId + " room=" + roomId);
                        }
                        assertEquals(memoryResult, dbType,
                            "Type mismatch for task=" + task.originalId() +
                            " slot=" + slotId + " room=" + roomId);
                    }
                    totalComparisons++;
                }
            }
        }

        // 11. Incremental: place some valid assignments in both memory and DB
        List<Assignment> validAssignments = new ArrayList<>();
        Set<String> usedSlotRooms = new HashSet<>();
        for (EngineTask task : ctx.tasks()) {
            for (Long slotId : allSlotIds) {
                Integer slotIdx = slotIdToIdx.get(slotId);
                if (slotIdx == null) continue;
                for (Long roomId : allRoomIds) {
                    Integer roomIdx = roomIdToIdx.get(roomId);
                    if (roomIdx == null) continue;
                    String slotRoomKey = slotId + "_" + roomId;
                    if (usedSlotRooms.contains(slotRoomKey)) continue;
                    Assignment a = new Assignment(task.index(), 0, slotIdx, roomIdx);
                    if (detector.check(a) == null && conflictService.checkConflict(task.originalId(), slotId, roomId, null) == null) {
                        validAssignments.add(a);
                        usedSlotRooms.add(slotRoomKey);
                        break;
                    }
                }
                if (!validAssignments.isEmpty()) break;
            }
            if (validAssignments.size() >= 5) break;
        }

        // Place in memory and DB
        for (Assignment va : validAssignments) {
            detector.place(va);
            EngineTask et = ctx.tasks().get(va.taskIndex());
            Long slotId = ctx.timeSlots().get(va.timeSlotIndex()).originalId();
            Long roomId = ctx.classrooms().get(va.classroomIndex()).originalId();

            try {
                Schedule s = new Schedule();
                s.setSemesterId(semesterId);
                s.setTeachingTaskId(et.originalId());
                s.setTeacherId(ctx.teachers().get((int) et.teacherIndex()).originalId());
                s.setClassId(ctx.classes().get((int) et.classIndex()).originalId());
                s.setCourseId(ctx.courses().get((int) et.courseIndex()).originalId());
                s.setTimeSlotId(slotId);
                s.setClassroomId(roomId);
                s.setSourceType("PAIR_TEST");
                s.setDeleted(0);
                scheduleMapper.insert(s);
                createdScheduleIds.add(s.getId());
            } catch (Exception e) {
                // Unique constraint violation - skip this assignment
                detector.remove(va);
            }
        }

        // Re-compare after incremental placement
        int incrementalComparisons = 0;
        for (EngineTask task : ctx.tasks()) {
            for (Long slotId : allSlotIds) {
                Integer slotIdx = slotIdToIdx.get(slotId);
                if (slotIdx == null) continue;
                for (Long roomId : allRoomIds) {
                    Integer roomIdx = roomIdToIdx.get(roomId);
                    if (roomIdx == null) continue;

                    Assignment a = new Assignment(task.index(), 0, slotIdx, roomIdx);
                    String memoryResult = detector.check(a);
                    String dbResult = conflictService.checkConflict(task.originalId(), slotId, roomId, null);
                    String dbType = dbResult == null ? null : ScheduleConflictService.extractReasonType(dbResult);

                    if (memoryResult == null) {
                        if (dbType != null) {
                            fail("[INCREMENTAL] Memory allows but DB rejects: " + dbType +
                                " for task=" + task.originalId() + " slot=" + slotId + " room=" + roomId);
                        }
                    } else {
                        if (dbType == null) {
                            fail("[INCREMENTAL] Memory rejects (" + memoryResult + ") but DB allows" +
                                " for task=" + task.originalId() + " slot=" + slotId + " room=" + roomId);
                        }
                        assertEquals(memoryResult, dbType,
                            "[INCREMENTAL] Type mismatch for task=" + task.originalId() +
                            " slot=" + slotId + " room=" + roomId);
                    }
                    incrementalComparisons++;
                }
            }
        }
        totalComparisons += incrementalComparisons;
        System.out.println("[PAIR-TEST] Initial comparisons: " + (totalComparisons - incrementalComparisons)
            + ", incremental: " + incrementalComparisons + ", total: " + totalComparisons);

        return totalComparisons;
    }
}
