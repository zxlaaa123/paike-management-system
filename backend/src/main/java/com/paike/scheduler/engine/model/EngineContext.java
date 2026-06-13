package com.paike.scheduler.engine.model;

import java.util.List;
import java.util.Map;

/**
 * 不可变内存模型：排课开始时一次性加载学期全量数据。
 * 引擎运行期间禁止任何 Mapper / Service 调用。
 */
public record EngineContext(
    List<EngineTask> tasks,
    List<TimeSlotData> timeSlots,
    List<ClassroomData> classrooms,
    List<TeacherData> teachers,
    List<ClassData> classes,
    List<CourseData> courses,
    boolean[][] teacherUnavailable,
    boolean[] teacherDisabled,
    boolean[] classDisabled,
    boolean[] classroomDisabled,
    int teacherMaxDailySlots,
    int classMaxDailySlots,
    boolean allowSameCourseSameDay,
    int afternoonStartPeriod,
    Map<String, Double> ruleWeights,
    List<Assignment> lockedAssignments,
    List<Assignment> existingScheduleAssignments,
    int[] existingTaskScheduledCount
) {
    public record TimeSlotData(int index, long originalId, int dayOfWeek, int periodNo) {}

    public record ClassroomData(int index, long originalId, Integer capacity, String roomType) {}

    public record TeacherData(int index, long originalId, String name, int status) {}

    public record ClassData(int index, long originalId, Integer studentCount, int status) {}

    public record CourseData(int index, long originalId, String courseType) {}

    public int taskCount() {
        return tasks.size();
    }

    public int timeSlotCount() {
        return timeSlots.size();
    }

    public int classroomCount() {
        return classrooms.size();
    }
}
