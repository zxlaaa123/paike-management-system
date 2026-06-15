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
    /**
     * V9 阶段3 方案 X：每个物理时段翻倍为两个逻辑 slot（ODD + EVEN），weekType 标识其所属周次。
     * ODD 任务只能占用 weekType=ODD 的 slot，EVEN 任务只能占用 EVEN 的，ALL 任务可占两者。
     * 翻倍后 timeSlotIndex 隐式编码 weekType，引擎核心（Detector/Neighbor/Backtracking）零改动。
     */
    public record TimeSlotData(int index, long originalId, int dayOfWeek, int periodNo, String weekType) {}

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
