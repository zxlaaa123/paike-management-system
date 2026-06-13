package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 从 Mapper 装载学期全量数据，构建不可变的 EngineContext。
 * 事务内一次性完成，保证数据一致性。
 */
@Service
@RequiredArgsConstructor
public class EngineContextLoader {

    private final TeachingTaskMapper teachingTaskMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final CourseMapper courseMapper;
    private final TeacherUnavailableTimeService unavailableTimeService;
    private final ScheduleRuleService ruleService;
    private final ScheduleLockedItemMapper lockedItemMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleRuleWeightMapper ruleWeightMapper;
    private final ScheduleThresholdProperties thresholdProperties;

    @Transactional(readOnly = true)
    public EngineContext load(Long semesterId) {
        // 1. Load core data
        List<TeachingTask> tasks = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getSemesterId, semesterId)
                .eq(TeachingTask::getStatus, 1));

        List<TimeSlot> timeSlots = timeSlotMapper.selectList(
            new LambdaQueryWrapper<TimeSlot>().orderByAsc(TimeSlot::getSortOrder));

        // Load ALL classrooms (including disabled, for pair test alignment)
        List<Classroom> allClassrooms = classroomMapper.selectList(new LambdaQueryWrapper<>());

        // Load ALL teachers and classes (including disabled, for pair test alignment)
        List<Teacher> allTeachers = teacherMapper.selectList(new LambdaQueryWrapper<>());
        List<ClassInfo> allClasses = classInfoMapper.selectList(new LambdaQueryWrapper<>());
        List<Course> allCourses = courseMapper.selectList(new LambdaQueryWrapper<>());

        // 2. Build index maps
        Map<Long, Integer> teacherIdxMap = new HashMap<>();
        Map<Long, Integer> classIdxMap = new HashMap<>();
        Map<Long, Integer> courseIdxMap = new HashMap<>();
        Map<Long, Integer> slotIdxMap = new HashMap<>();
        Map<Long, Integer> roomIdxMap = new HashMap<>();

        List<EngineContext.TeacherData> teacherDataList = new ArrayList<>();
        for (int i = 0; i < allTeachers.size(); i++) {
            Teacher t = allTeachers.get(i);
            teacherIdxMap.put(t.getId(), i);
            teacherDataList.add(new EngineContext.TeacherData(i, t.getId(), t.getName(),
                t.getStatus() != null ? t.getStatus() : 0));
        }

        List<EngineContext.ClassData> classDataList = new ArrayList<>();
        for (int i = 0; i < allClasses.size(); i++) {
            ClassInfo c = allClasses.get(i);
            classIdxMap.put(c.getId(), i);
            classDataList.add(new EngineContext.ClassData(i, c.getId(), c.getStudentCount(),
                c.getStatus() != null ? c.getStatus() : 0));
        }

        List<EngineContext.CourseData> courseDataList = new ArrayList<>();
        for (int i = 0; i < allCourses.size(); i++) {
            Course c = allCourses.get(i);
            courseIdxMap.put(c.getId(), i);
            courseDataList.add(new EngineContext.CourseData(i, c.getId(), c.getCourseType()));
        }

        List<EngineContext.TimeSlotData> slotDataList = new ArrayList<>();
        for (int i = 0; i < timeSlots.size(); i++) {
            TimeSlot s = timeSlots.get(i);
            slotIdxMap.put(s.getId(), i);
            slotDataList.add(new EngineContext.TimeSlotData(i, s.getId(),
                s.getDayOfWeek() != null ? s.getDayOfWeek() : 0,
                s.getPeriodNo() != null ? s.getPeriodNo() : 0));
        }

        List<EngineContext.ClassroomData> roomDataList = new ArrayList<>();
        for (int i = 0; i < allClassrooms.size(); i++) {
            Classroom r = allClassrooms.get(i);
            roomIdxMap.put(r.getId(), i);
            roomDataList.add(new EngineContext.ClassroomData(i, r.getId(), r.getCapacity(), r.getRoomType()));
        }

        // 3. Disabled flags
        boolean[] teacherDisabled = new boolean[teacherDataList.size()];
        for (int i = 0; i < teacherDataList.size(); i++) {
            teacherDisabled[i] = teacherDataList.get(i).status() != 1;
        }

        boolean[] classDisabled = new boolean[classDataList.size()];
        for (int i = 0; i < classDataList.size(); i++) {
            classDisabled[i] = classDataList.get(i).status() != 1;
        }

        boolean[] classroomDisabled = new boolean[roomDataList.size()];
        for (int i = 0; i < roomDataList.size(); i++) {
            Classroom r = allClassrooms.get(i);
            classroomDisabled[i] = r.getStatus() == null || r.getStatus() != 1;
        }

        // 4. Teacher unavailable
        boolean[][] teacherUnavailable = new boolean[teacherDataList.size()][slotDataList.size()];
        for (Teacher t : allTeachers) {
            Integer tIdx = teacherIdxMap.get(t.getId());
            if (tIdx == null) continue;
            for (TimeSlot s : timeSlots) {
                Integer sIdx = slotIdxMap.get(s.getId());
                if (sIdx == null) continue;
                if (unavailableTimeService.isUnavailable(t.getId(), s.getId())) {
                    teacherUnavailable[tIdx][sIdx] = true;
                }
            }
        }

        // 5. Rules
        int teacherMaxDailySlots = ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS");
        int classMaxDailySlots = ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS");
        boolean allowSameCourseSameDay = ruleService.getBoolValue("ALLOW_SAME_COURSE_SAME_DAY");

        // 6. Rule weights
        Map<String, Double> ruleWeights = new HashMap<>();
        List<ScheduleRuleWeight> weights = ruleWeightMapper.selectList(
            new LambdaQueryWrapper<ScheduleRuleWeight>()
                .eq(ScheduleRuleWeight::getSemesterId, semesterId)
                .eq(ScheduleRuleWeight::getRuleType, "SOFT")
                .eq(ScheduleRuleWeight::getEnabled, 1));
        for (ScheduleRuleWeight w : weights) {
            if (w.getRuleCode() != null && w.getWeight() != null) {
                ruleWeights.put(w.getRuleCode(), w.getWeight().doubleValue());
            }
        }

        // 7. Build engine tasks
        List<EngineTask> engineTasks = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            TeachingTask t = tasks.get(i);
            Integer tIdx = teacherIdxMap.get(t.getTeacherId());
            Integer cIdx = classIdxMap.get(t.getClassId());
            Integer coIdx = courseIdxMap.get(t.getCourseId());

            if (tIdx == null || cIdx == null || coIdx == null) continue;

            int weeklyHours = t.getWeeklyHours() != null ? t.getWeeklyHours() : 0;
            int requiredSlots = (int) Math.ceil(weeklyHours / 2.0);

            String courseType = courseDataList.get(coIdx).courseType();
            Integer rawStudentCount = classDataList.get(cIdx).studentCount();
            int studentCount = rawStudentCount != null ? rawStudentCount : -1;

            List<Integer> candidateRooms = new ArrayList<>();
            for (int r = 0; r < roomDataList.size(); r++) {
                EngineContext.ClassroomData room = roomDataList.get(r);
                if (room.capacity() != null && studentCount >= 0
                    && room.capacity() >= studentCount
                    && isRoomTypeMatched(courseType, room.roomType())) {
                    candidateRooms.add(r);
                }
            }

            int engineTaskIndex = engineTasks.size();
            engineTasks.add(new EngineTask(engineTaskIndex, t.getId(), tIdx, cIdx, coIdx, requiredSlots,
                courseType, studentCount, candidateRooms));
        }

        // 8. Load existing schedules as initial occupancy
        List<Schedule> existingSchedules = scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId));

        List<Assignment> existingAssignments = new ArrayList<>();
        int[] existingTaskScheduledCount = new int[engineTasks.size()];

        // Build taskId → engineTaskIndex map
        Map<Long, Integer> taskIdToEngineIdx = new HashMap<>();
        for (int i = 0; i < engineTasks.size(); i++) {
            taskIdToEngineIdx.put(engineTasks.get(i).originalId(), i);
        }

        for (Schedule s : existingSchedules) {
            Integer slotIdx = slotIdxMap.get(s.getTimeSlotId());
            Integer roomIdx = roomIdxMap.get(s.getClassroomId());
            Integer taskEngineIdx = s.getTeachingTaskId() != null ? taskIdToEngineIdx.get(s.getTeachingTaskId()) : null;

            if (slotIdx == null || roomIdx == null) continue;

            if (taskEngineIdx != null) {
                existingTaskScheduledCount[taskEngineIdx]++;
                existingAssignments.add(new Assignment(taskEngineIdx, 0, slotIdx, roomIdx));
            }
        }

        // 9. Locked assignments
        List<Assignment> lockedAssignments = new ArrayList<>();
        List<ScheduleLockedItem> lockedItems = lockedItemMapper.selectList(
            new LambdaQueryWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getActiveFlag, 1));

        for (ScheduleLockedItem item : lockedItems) {
            if (item.getPlanItemId() == null) continue;
            SchedulePlanItem planItem = planItemMapper.selectById(item.getPlanItemId());
            if (planItem == null) continue;

            Long taskId = planItem.getTeachingTaskId();
            Integer weekday = planItem.getWeekday();
            Integer startPeriod = planItem.getStartPeriod();

            if (taskId == null || weekday == null || startPeriod == null) continue;

            Integer taskIdx = taskIdToEngineIdx.get(taskId);
            if (taskIdx == null) continue;

            int periodNo = (startPeriod + 1) / 2;
            Integer slotIdx = null;
            for (int i = 0; i < slotDataList.size(); i++) {
                if (slotDataList.get(i).dayOfWeek() == weekday && slotDataList.get(i).periodNo() == periodNo) {
                    slotIdx = i;
                    break;
                }
            }
            if (slotIdx == null) continue;

            Integer roomIdx = null;
            if (planItem.getClassroomId() != null) {
                roomIdx = roomIdxMap.get(planItem.getClassroomId());
            }
            if (roomIdx == null) continue;

            lockedAssignments.add(new Assignment(taskIdx, 0, slotIdx, roomIdx));
        }

        return new EngineContext(engineTasks, slotDataList, roomDataList, teacherDataList,
            classDataList, courseDataList, teacherUnavailable, teacherDisabled, classDisabled,
            classroomDisabled, teacherMaxDailySlots, classMaxDailySlots, allowSameCourseSameDay,
            thresholdProperties.getAfternoonStartPeriod(), ruleWeights, lockedAssignments, existingAssignments, existingTaskScheduledCount);
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
