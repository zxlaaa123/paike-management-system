package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.ScheduleRuleService;
import com.paike.scheduler.service.TeacherUnavailableTimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public EngineContext load(Long semesterId) {
        List<TeachingTask> tasks = teachingTaskMapper.selectList(
            new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getSemesterId, semesterId)
                .eq(TeachingTask::getStatus, 1));

        List<TimeSlot> timeSlots = timeSlotMapper.selectList(
            new LambdaQueryWrapper<TimeSlot>().orderByAsc(TimeSlot::getSortOrder));

        List<Classroom> classrooms = classroomMapper.selectList(
            new LambdaQueryWrapper<Classroom>()
                .eq(Classroom::getStatus, 1)
                .eq(Classroom::getDeleted, 0));

        Set<Long> teacherIds = tasks.stream().map(TeachingTask::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> classIds = tasks.stream().map(TeachingTask::getClassId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> courseIds = tasks.stream().map(TeachingTask::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());

        List<Teacher> teachers = teacherIds.isEmpty() ? List.of() :
            teacherMapper.selectBatchIds(teacherIds);
        List<ClassInfo> classes = classIds.isEmpty() ? List.of() :
            classInfoMapper.selectBatchIds(classIds);
        List<Course> courses = courseIds.isEmpty() ? List.of() :
            courseMapper.selectBatchIds(courseIds);

        Map<Long, Integer> teacherIdxMap = new HashMap<>();
        Map<Long, Integer> classIdxMap = new HashMap<>();
        Map<Long, Integer> courseIdxMap = new HashMap<>();
        Map<Long, Integer> slotIdxMap = new HashMap<>();
        Map<Long, Integer> roomIdxMap = new HashMap<>();

        List<EngineContext.TeacherData> teacherDataList = new ArrayList<>();
        for (int i = 0; i < teachers.size(); i++) {
            Teacher t = teachers.get(i);
            teacherIdxMap.put(t.getId(), i);
            teacherDataList.add(new EngineContext.TeacherData(i, t.getId(), t.getName(), t.getStatus() != null ? t.getStatus() : 0));
        }

        List<EngineContext.ClassData> classDataList = new ArrayList<>();
        for (int i = 0; i < classes.size(); i++) {
            ClassInfo c = classes.get(i);
            classIdxMap.put(c.getId(), i);
            classDataList.add(new EngineContext.ClassData(i, c.getId(), c.getStudentCount() != null ? c.getStudentCount() : 0, c.getStatus() != null ? c.getStatus() : 0));
        }

        List<EngineContext.CourseData> courseDataList = new ArrayList<>();
        for (int i = 0; i < courses.size(); i++) {
            Course c = courses.get(i);
            courseIdxMap.put(c.getId(), i);
            courseDataList.add(new EngineContext.CourseData(i, c.getId(), c.getCourseType()));
        }

        List<EngineContext.TimeSlotData> slotDataList = new ArrayList<>();
        for (int i = 0; i < timeSlots.size(); i++) {
            TimeSlot s = timeSlots.get(i);
            slotIdxMap.put(s.getId(), i);
            slotDataList.add(new EngineContext.TimeSlotData(i, s.getId(), s.getDayOfWeek() != null ? s.getDayOfWeek() : 0, s.getPeriodNo() != null ? s.getPeriodNo() : 0));
        }

        List<EngineContext.ClassroomData> roomDataList = new ArrayList<>();
        for (int i = 0; i < classrooms.size(); i++) {
            Classroom r = classrooms.get(i);
            roomIdxMap.put(r.getId(), i);
            roomDataList.add(new EngineContext.ClassroomData(i, r.getId(), r.getCapacity() != null ? r.getCapacity() : 0, r.getRoomType()));
        }

        boolean[][] teacherUnavailable = new boolean[teacherDataList.size()][slotDataList.size()];
        for (Teacher t : teachers) {
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

        int teacherMaxDailySlots = ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS");
        int classMaxDailySlots = ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS");
        boolean allowSameCourseSameDay = ruleService.getBoolValue("ALLOW_SAME_COURSE_SAME_DAY");

        Map<String, Double> ruleWeights = new HashMap<>();

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
            int studentCount = classDataList.get(cIdx).studentCount();

            List<Integer> candidateRooms = new ArrayList<>();
            for (int r = 0; r < roomDataList.size(); r++) {
                EngineContext.ClassroomData room = roomDataList.get(r);
                if (room.capacity() >= studentCount && isRoomTypeMatched(courseType, room.roomType())) {
                    candidateRooms.add(r);
                }
            }

            engineTasks.add(new EngineTask(i, t.getId(), tIdx, cIdx, coIdx, requiredSlots, courseType, studentCount, candidateRooms));
        }

        List<Assignment> lockedAssignments = new ArrayList<>();
        List<ScheduleLockedItem> lockedItems = lockedItemMapper.selectList(
            new LambdaQueryWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getActiveFlag, 1)
                .eq(ScheduleLockedItem::getDeleted, 0));

        for (ScheduleLockedItem item : lockedItems) {
            if (item.getPlanItemId() == null) continue;
            SchedulePlanItem planItem = planItemMapper.selectById(item.getPlanItemId());
            if (planItem == null) continue;

            Long taskId = planItem.getTeachingTaskId();
            Integer weekday = planItem.getWeekday();
            Integer startPeriod = planItem.getStartPeriod();

            if (taskId == null || weekday == null || startPeriod == null) continue;

            Integer taskIdx = null;
            for (int i = 0; i < engineTasks.size(); i++) {
                if (engineTasks.get(i).originalId() == taskId) {
                    taskIdx = i;
                    break;
                }
            }
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

        return new EngineContext(engineTasks, slotDataList, roomDataList, teacherDataList, classDataList, courseDataList, teacherUnavailable, teacherMaxDailySlots, classMaxDailySlots, allowSameCourseSameDay, ruleWeights, lockedAssignments);
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
