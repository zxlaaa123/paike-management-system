package com.paike.scheduler.engine.conflict;

import com.paike.scheduler.service.EngineContextLoader;
import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.ScheduleConflictService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConflictDetectorPairTest {

    @Autowired
    private EngineContextLoader contextLoader;

    @Autowired
    private ScheduleConflictService conflictService;

    @Autowired
    private TeachingTaskMapper teachingTaskMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private ClassroomMapper classroomMapper;

    @Autowired
    private ScheduleMapper scheduleMapper;

    @Autowired
    private SemesterMapper semesterMapper;

    @Test
    void testPairComparison() {
        List<Semester> semesters = semesterMapper.selectList(null);
        if (semesters.isEmpty()) {
            return;
        }

        for (Semester semester : semesters) {
            EngineContext ctx = contextLoader.load(semester.getId());
            if (ctx.taskCount() == 0 || ctx.timeSlotCount() == 0 || ctx.classroomCount() == 0) {
                continue;
            }

            InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);

            List<TimeSlot> timeSlots = timeSlotMapper.selectList(null);
            List<Classroom> classrooms = classroomMapper.selectList(null);

            Map<Long, Integer> slotIdToIdx = new HashMap<>();
            for (EngineContext.TimeSlotData s : ctx.timeSlots()) {
                slotIdToIdx.put(s.originalId(), s.index());
            }

            Map<Long, Integer> roomIdToIdx = new HashMap<>();
            for (EngineContext.ClassroomData r : ctx.classrooms()) {
                roomIdToIdx.put(r.originalId(), r.index());
            }

            for (EngineTask task : ctx.tasks()) {
                for (TimeSlot slot : timeSlots) {
                    Integer slotIdx = slotIdToIdx.get(slot.getId());
                    if (slotIdx == null) continue;

                    for (Classroom room : classrooms) {
                        if (room.getDeleted() != null && room.getDeleted() == 1) continue;
                        if (room.getStatus() == null || room.getStatus() != 1) continue;

                        Integer roomIdx = roomIdToIdx.get(room.getId());
                        if (roomIdx == null) continue;

                        Assignment a = new Assignment(task.index(), 0, slotIdx, roomIdx);
                        String memoryResult = detector.check(a);

                        String dbResult = conflictService.checkConflict(task.originalId(), slot.getId(), room.getId(), null);
                        String dbType = dbResult == null ? null : ScheduleConflictService.extractReasonType(dbResult);

                        if (memoryResult == null) {
                            assertNull(dbType, "Memory allows but DB rejects: " + dbType +
                                " for task=" + task.originalId() + " slot=" + slot.getId() + " room=" + room.getId());
                        } else {
                            assertNotNull(dbType, "Memory rejects (" + memoryResult + ") but DB allows" +
                                " for task=" + task.originalId() + " slot=" + slot.getId() + " room=" + room.getId());
                            assertEquals(memoryResult, dbType, "Type mismatch for task=" + task.originalId() +
                                " slot=" + slot.getId() + " room=" + room.getId());
                        }
                    }
                }
            }
        }
    }
}
