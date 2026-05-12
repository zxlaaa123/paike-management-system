package com.paike.scheduler.service;

import com.paike.scheduler.entity.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeacherUnavailableTimeMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoScheduleService {

    private final AutoScheduleBatchService batchService;
    private final UnscheduledTaskService unscheduledTaskService;
    private final ScheduleConflictService conflictService;
    private final ScheduleRuleService ruleService;
    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final TeacherUnavailableTimeMapper unavailableTimeMapper;
    private final CourseMapper courseMapper;
    private final ClassInfoMapper classInfoMapper;

    @Transactional
    public AutoScheduleResult run(AutoScheduleRequest request) {
        // 1. 清空旧排课（如需要）
        if (request.isClearAllSchedule()) {
            scheduleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getDeleted, 0));
        } else if (request.isClearOldAutoSchedule()) {
            scheduleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getSourceType, "AUTO")
                    .eq(Schedule::getDeleted, 0));
        }

        // 2. 读取待排教学任务
        List<TeachingTask> allTasks = teachingTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeachingTask>()
                        .eq(TeachingTask::getDeleted, 0)
                        .eq(TeachingTask::getStatus, 1));

        List<TeachingTask> targetTasks;
        if (request.getTaskIds() != null && !request.getTaskIds().isEmpty()) {
            targetTasks = allTasks.stream()
                    .filter(t -> request.getTaskIds().contains(t.getId()))
                    .collect(Collectors.toList());
        } else {
            targetTasks = allTasks;
        }

        // 3. 创建批次
        AutoScheduleBatch batch = batchService.createBatch(targetTasks.size(), request.isClearOldAutoSchedule());

        // 4. 读取规则配置
        int teacherMaxDailySlots = ruleService.getIntValue("TEACHER_MAX_DAILY_SLOTS");
        int classMaxDailySlots = ruleService.getIntValue("CLASS_MAX_DAILY_SLOTS");
        boolean prioritizeMorning = ruleService.getBoolValue("PRIORITIZE_MORNING");
        boolean avoidFridayAfternoon = ruleService.getBoolValue("AVOID_FRIDAY_AFTERNOON");
        boolean allowSameCourseSameDay = ruleService.getBoolValue("ALLOW_SAME_COURSE_SAME_DAY");

        // 5. 读取时间段并排序
        List<TimeSlot> timeSlots = timeSlotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TimeSlot>()
                        .orderByAsc(TimeSlot::getSortOrder));
        timeSlots = sortTimeSlots(timeSlots, prioritizeMorning, avoidFridayAfternoon);

        // 6. 读取可用教室
        List<Classroom> classrooms = classroomMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Classroom>()
                        .eq(Classroom::getStatus, 1)
                        .eq(Classroom::getDeleted, 0));

        // 7. 读取教师禁排时间
        List<TeacherUnavailableTime> unavailableTimes = unavailableTimeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeacherUnavailableTime>()
                        .eq(TeacherUnavailableTime::getStatus, 1)
                        .eq(TeacherUnavailableTime::getDeleted, 0));
        Set<String> unavailableKeySet = unavailableTimes.stream()
                .map(ut -> ut.getTeacherId() + "_" + ut.getTimeSlotId())
                .collect(Collectors.toSet());

        // 8. 对教学任务排序（难排优先）
        targetTasks = sortTasks(targetTasks, unavailableTimes);

        // 9. 遍历排课
        int generatedCount = 0;
        int successTaskCount = 0;
        int failedTaskCount = 0;

        for (TeachingTask task : targetTasks) {
            // 计算需要排的大节数
            int requiredSlots = (int) Math.ceil(task.getWeeklyHours() / 2.0);
            int scheduledSlots = countScheduledSlots(task.getId());
            int remainingSlots = requiredSlots - scheduledSlots;

            if (remainingSlots <= 0) {
                successTaskCount++;
                continue;
            }

            // 预过滤：符合课程类型+容量+停用的教室
            String courseType = getCourseType(task.getCourseId());
            int studentCount = getClassStudentCount(task.getClassId());
            List<Classroom> matchedRooms = classrooms.stream()
                    .filter(r -> r.getCapacity() >= studentCount)
                    .filter(r -> isRoomTypeMatched(courseType, r.getRoomType()))
                    .sorted(Comparator.comparingInt(Classroom::getCapacity))
                    .collect(Collectors.toList());

            if (matchedRooms.isEmpty()) {
                unscheduledTaskService.addUnscheduledTask(batch.getId(), task.getId(), requiredSlots,
                        scheduledSlots, remainingSlots, "NO_MATCHED_CLASSROOM", "没有符合课程类型和容量要求的教室");
                failedTaskCount++;
                continue;
            }

            int currentSuccess = 0;
            String lastFailReason = "";
            Set<Integer> usedDays = new HashSet<>();

            for (int i = 0; i < remainingSlots; i++) {
                boolean arranged = false;

                for (TimeSlot slot : timeSlots) {
                    // 跳过教师禁排时间
                    if (unavailableKeySet.contains(task.getTeacherId() + "_" + slot.getId())) {
                        lastFailReason = "教师禁排时间限制";
                        continue;
                    }

                    // 检查教师每日最大课程数
                    if (!checkTeacherDailyLimit(task.getTeacherId(), slot.getDayOfWeek(), teacherMaxDailySlots, batch.getId())) {
                        lastFailReason = "教师每天最多" + teacherMaxDailySlots + "个大节";
                        continue;
                    }

                    // 检查班级每日最大课程数
                    if (!checkClassDailyLimit(task.getClassId(), slot.getDayOfWeek(), classMaxDailySlots, batch.getId())) {
                        lastFailReason = "班级每天最多" + classMaxDailySlots + "个大节";
                        continue;
                    }

                    // 检查同一课程同一天重复
                    if (!allowSameCourseSameDay && usedDays.contains(slot.getDayOfWeek())) {
                        // 检查当天是否已有同一课程
                        if (hasSameCourseSameDay(task.getClassId(), task.getCourseId(), slot.getDayOfWeek(), batch.getId())) {
                            lastFailReason = "同一课程同一天不允许重复";
                            continue;
                        }
                    }

                    for (Classroom room : matchedRooms) {
                        // 复用冲突检测
                        String conflict = conflictService.checkConflict(task.getId(), slot.getId(), room.getId(), null);
                        if (conflict == null) {
                            // 无冲突，保存排课记录
                            saveSchedule(task, slot, room, batch.getId());
                            generatedCount++;
                            currentSuccess++;
                            arranged = true;
                            usedDays.add(slot.getDayOfWeek());
                            break;
                        } else {
                            lastFailReason = conflict.replace("排课失败：", "");
                        }
                    }

                    if (arranged) break;
                }

                if (!arranged) {
                    break;
                }
            }

            if (currentSuccess > 0) {
                successTaskCount++;
            }
            if (currentSuccess < remainingSlots) {
                failedTaskCount++;
                String reasonType = categorizeReason(lastFailReason);
                unscheduledTaskService.addUnscheduledTask(batch.getId(), task.getId(), requiredSlots,
                        scheduledSlots + currentSuccess, remainingSlots - currentSuccess,
                        reasonType, lastFailReason);
            }
        }

        // 10. 更新批次状态
        String status;
        String message;
        if (failedTaskCount == 0) {
            status = "SUCCESS";
            message = "自动排课完成，全部任务已安排";
        } else if (successTaskCount > 0) {
            status = "PARTIAL";
            message = "自动排课完成，部分任务未排满";
        } else {
            status = "FAILED";
            message = "自动排课完成，所有任务均未安排";
        }

        batchService.updateBatchResult(batch.getId(), successTaskCount, failedTaskCount, generatedCount, status, message);

        // 11. 构建返回结果
        AutoScheduleResult result = new AutoScheduleResult();
        result.setBatchId(batch.getId());
        result.setBatchNo(batch.getBatchNo());
        result.setTotalTaskCount(targetTasks.size());
        result.setSuccessTaskCount(successTaskCount);
        result.setFailedTaskCount(failedTaskCount);
        result.setGeneratedScheduleCount(generatedCount);
        result.setStatus(status);
        result.setMessage(message);
        return result;
    }

    // ========== 排序方法 ==========

    private List<TeachingTask> sortTasks(List<TeachingTask> tasks, List<TeacherUnavailableTime> unavailableTimes) {
        Map<Long, Long> unavailableCount = unavailableTimes.stream()
                .collect(Collectors.groupingBy(TeacherUnavailableTime::getTeacherId, Collectors.counting()));

        return tasks.stream().sorted((a, b) -> {
            // 1. 实验课、机房课优先
            String typeA = getCourseType(a.getCourseId());
            String typeB = getCourseType(b.getCourseId());
            int priorityA = ("EXPERIMENT".equals(typeA) || "COMPUTER".equals(typeA)) ? 0 : 1;
            int priorityB = ("EXPERIMENT".equals(typeB) || "COMPUTER".equals(typeB)) ? 0 : 1;
            if (priorityA != priorityB) return priorityA - priorityB;

            // 2. 班级人数多的优先
            int countA = getClassStudentCount(a.getClassId());
            int countB = getClassStudentCount(b.getClassId());
            if (countB != countA) return countB - countA;

            // 3. 每周课时多的优先
            if (b.getWeeklyHours() != a.getWeeklyHours()) return b.getWeeklyHours() - a.getWeeklyHours();

            // 4. 教师禁排时间多的优先
            long unavailA = unavailableCount.getOrDefault(a.getTeacherId(), 0L);
            long unavailB = unavailableCount.getOrDefault(b.getTeacherId(), 0L);
            return Long.compare(unavailB, unavailA);
        }).collect(Collectors.toList());
    }

    private List<TimeSlot> sortTimeSlots(List<TimeSlot> slots, boolean prioritizeMorning, boolean avoidFridayAfternoon) {
        return slots.stream().sorted((a, b) -> {
            if (prioritizeMorning) {
                boolean aMorning = a.getPeriodNo() <= 2;
                boolean bMorning = b.getPeriodNo() <= 2;
                if (aMorning != bMorning) return aMorning ? -1 : 1;
            }
            if (avoidFridayAfternoon) {
                boolean aFriPm = a.getDayOfWeek() == 5 && a.getPeriodNo() >= 3;
                boolean bFriPm = b.getDayOfWeek() == 5 && b.getPeriodNo() >= 3;
                if (aFriPm != bFriPm) return aFriPm ? 1 : -1;
            }
            return a.getSortOrder() - b.getSortOrder();
        }).collect(Collectors.toList());
    }


    // ========== 辅助方法 ==========

    private boolean isRoomTypeMatched(String courseType, String roomType) {
        if ("EXPERIMENT".equals(courseType)) return "LAB".equals(roomType);
        if ("COMPUTER".equals(courseType)) return "COMPUTER".equals(roomType);
        // 普通课和体育课不限
        return true;
    }

    private int countScheduledSlots(Long taskId) {
        return scheduleMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getTeachingTaskId, taskId)
                        .eq(Schedule::getDeleted, 0)).intValue();
    }

    private boolean checkTeacherDailyLimit(Long teacherId, int dayOfWeek, int maxSlots, Long currentBatchId) {
        List<Long> slotIds = getTimeSlotIdsByDay(dayOfWeek);
        if (slotIds.isEmpty()) return true;
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getTeacherId, teacherId)
                .eq(Schedule::getDeleted, 0)
                .in(Schedule::getTimeSlotId, slotIds);
        if (currentBatchId != null) {
            wrapper.ne(Schedule::getBatchId, currentBatchId);
        }
        long count = scheduleMapper.selectCount(wrapper);
        return count < maxSlots;
    }

    private boolean checkClassDailyLimit(Long classId, int dayOfWeek, int maxSlots, Long currentBatchId) {
        List<Long> slotIds = getTimeSlotIdsByDay(dayOfWeek);
        if (slotIds.isEmpty()) return true;
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getClassId, classId)
                .eq(Schedule::getDeleted, 0)
                .in(Schedule::getTimeSlotId, slotIds);
        if (currentBatchId != null) {
            wrapper.ne(Schedule::getBatchId, currentBatchId);
        }
        long count = scheduleMapper.selectCount(wrapper);
        return count < maxSlots;
    }

    private List<Long> getTimeSlotIdsByDay(int dayOfWeek) {
        return timeSlotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TimeSlot>()
                        .eq(TimeSlot::getDayOfWeek, dayOfWeek))
                .stream().map(TimeSlot::getId).collect(Collectors.toList());
    }

    private boolean hasSameCourseSameDay(Long classId, Long courseId, int dayOfWeek, Long batchId) {
        List<Long> slotIds = getTimeSlotIdsByDay(dayOfWeek);
        if (slotIds.isEmpty()) return false;
        long count = scheduleMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getClassId, classId)
                        .eq(Schedule::getCourseId, courseId)
                        .eq(Schedule::getDeleted, 0)
                        .in(Schedule::getTimeSlotId, slotIds));
        return count > 0;
    }

    private void saveSchedule(TeachingTask task, TimeSlot slot, Classroom room, Long batchId) {
        Schedule schedule = new Schedule();
        schedule.setTeachingTaskId(task.getId());
        schedule.setCourseId(task.getCourseId());
        schedule.setTeacherId(task.getTeacherId());
        schedule.setClassId(task.getClassId());
        schedule.setTimeSlotId(slot.getId());
        schedule.setClassroomId(room.getId());
        schedule.setSourceType("AUTO");
        schedule.setBatchId(batchId);
        schedule.setDeleted(0);
        schedule.setCreateTime(LocalDateTime.now());
        schedule.setUpdateTime(LocalDateTime.now());
        scheduleMapper.insert(schedule);
    }

    private String getCourseType(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        return course != null ? course.getCourseType() : "NORMAL";
    }

    private int getClassStudentCount(Long classId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        return classInfo != null ? classInfo.getStudentCount() : 0;
    }

    // 修正：直接注入所需的 mapper
    // 上面的 getCourseType 和 getClassStudentCount 写法不对，改为注入

    private String categorizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "UNKNOWN";
        if (reason.contains("教师禁排")) return "TEACHER_UNAVAILABLE";
        if (reason.contains("已有课程") && reason.contains("老师")) return "TEACHER_CONFLICT";
        if (reason.contains("已有课程") && !reason.contains("老师")) return "CLASS_CONFLICT";
        if (reason.contains("教室") && reason.contains("占用")) return "ROOM_CONFLICT";
        if (reason.contains("容量")) return "CLASSROOM_CAPACITY_NOT_ENOUGH";
        if (reason.contains("实验课")) return "ROOM_TYPE_MISMATCH";
        if (reason.contains("机房课")) return "ROOM_TYPE_MISMATCH";
        if (reason.contains("每周课时")) return "TASK_NOT_FULLY_SCHEDULED";
        if (reason.contains("教师每天")) return "TEACHER_DAILY_LIMIT";
        if (reason.contains("班级每天")) return "CLASS_DAILY_LIMIT";
        if (reason.contains("同一课程同一天")) return "SAME_COURSE_SAME_DAY";
        if (reason.contains("没有符合")) return "NO_MATCHED_CLASSROOM";
        return "UNKNOWN";
    }

    // ========== DTO / VO ==========

    @Data
    public static class AutoScheduleRequest {
        private List<Long> taskIds;
        private boolean clearOldAutoSchedule = true;
        private boolean clearAllSchedule = false;
    }

    @Data
    public static class AutoScheduleResult {
        private Long batchId;
        private String batchNo;
        private int totalTaskCount;
        private int successTaskCount;
        private int failedTaskCount;
        private int generatedScheduleCount;
        private String status;
        private String message;
    }
}
