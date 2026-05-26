package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.TeacherUnavailableTime;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 排课相关的纯计算工具。
 * 所有方法无副作用、不依赖 Spring，便于直接单测。
 * 不要往这个类里塞需要 DB / 上下文的方法 —— 那种属于 service。
 */
public final class SchedulingSupport {

    private SchedulingSupport() {
    }

    /** 实验课/机房课需要对应专用房型；其他课型不限。 */
    public static boolean isRoomTypeMatched(String courseType, String roomType) {
        if (CourseType.EXPERIMENT.getCode().equals(courseType)) {
            return RoomType.LAB.getCode().equals(roomType);
        }
        if (CourseType.COMPUTER.getCode().equals(courseType)) {
            return RoomType.COMPUTER.getCode().equals(roomType);
        }
        return true;
    }

    public static String getCourseType(Long courseId, Map<Long, Course> courseMap) {
        Course course = courseMap.get(courseId);
        return course != null ? course.getCourseType() : CourseType.NORMAL.getCode();
    }

    public static int getClassStudentCount(Long classId, Map<Long, ClassInfo> classMap) {
        ClassInfo info = classMap.get(classId);
        if (info == null) {
            return 0;
        }
        return info.getStudentCount() != null ? info.getStudentCount() : 0;
    }

    /**
     * 难排任务优先：实验/机房 → 人数多 → 周课时多 → 教师禁排多。
     * 与原 AutoScheduleService.sortTasks 行为一致（含 V3 的 null 容错）。
     */
    public static List<TeachingTask> sortTasks(
            List<TeachingTask> tasks,
            Map<Long, Long> unavailableCountByTeacher,
            Map<Long, Course> courseMap,
            Map<Long, ClassInfo> classMap) {

        return tasks.stream().sorted((a, b) -> {
            int prA = isSpecialType(getCourseType(a.getCourseId(), courseMap)) ? 0 : 1;
            int prB = isSpecialType(getCourseType(b.getCourseId(), courseMap)) ? 0 : 1;
            if (prA != prB) return prA - prB;

            int cntA = getClassStudentCount(a.getClassId(), classMap);
            int cntB = getClassStudentCount(b.getClassId(), classMap);
            if (cntB != cntA) return cntB - cntA;

            int hA = a.getWeeklyHours() == null ? 0 : a.getWeeklyHours();
            int hB = b.getWeeklyHours() == null ? 0 : b.getWeeklyHours();
            if (hB != hA) return hB - hA;

            long uA = unavailableCountByTeacher.getOrDefault(a.getTeacherId(), 0L);
            long uB = unavailableCountByTeacher.getOrDefault(b.getTeacherId(), 0L);
            return Long.compare(uB, uA);
        }).collect(Collectors.toList());
    }

    /**
     * 偏好排序（不是硬限制）：可选优先上午、可选避开周五下午，最终按 sortOrder。
     * 容错：null 字段按最大值排到末尾。
     */
    public static List<TimeSlot> sortTimeSlots(
            List<TimeSlot> slots, boolean prioritizeMorning, boolean avoidFridayAfternoon) {

        return slots.stream().sorted((a, b) -> {
            int aPeriod = nullToMax(a.getPeriodNo());
            int bPeriod = nullToMax(b.getPeriodNo());
            int aDay = nullToMax(a.getDayOfWeek());
            int bDay = nullToMax(b.getDayOfWeek());

            if (prioritizeMorning) {
                boolean aM = aPeriod <= 2;
                boolean bM = bPeriod <= 2;
                if (aM != bM) return aM ? -1 : 1;
            }
            if (avoidFridayAfternoon) {
                boolean aFP = aDay == 5 && aPeriod >= 3;
                boolean bFP = bDay == 5 && bPeriod >= 3;
                if (aFP != bFP) return aFP ? 1 : -1;
            }
            return Integer.compare(nullToMax(a.getSortOrder()), nullToMax(b.getSortOrder()));
        }).collect(Collectors.toList());
    }

    /** 教师禁排时间转为 "teacherId_slotId" 快查集合。 */
    public static Set<String> toUnavailableKeySet(List<TeacherUnavailableTime> list) {
        return list.stream()
                .map(ut -> ut.getTeacherId() + "_" + ut.getTimeSlotId())
                .collect(Collectors.toSet());
    }

    /** 每位教师的禁排次数（排序权重用）。 */
    public static Map<Long, Long> toUnavailableCountByTeacher(List<TeacherUnavailableTime> list) {
        return list.stream()
                .collect(Collectors.groupingBy(
                        TeacherUnavailableTime::getTeacherId, Collectors.counting()));
    }

    /** 时间段按 dayOfWeek 分组成 id 列表，用于每日上限检查。 */
    public static Map<Integer, List<Long>> slotIdsByDay(List<TimeSlot> slots) {
        return slots.stream()
                .filter(s -> s.getDayOfWeek() != null)
                .collect(Collectors.groupingBy(
                        TimeSlot::getDayOfWeek,
                        Collectors.mapping(TimeSlot::getId, Collectors.toList())));
    }

    private static boolean isSpecialType(String type) {
        return CourseType.EXPERIMENT.getCode().equals(type)
                || CourseType.COMPUTER.getCode().equals(type);
    }

    private static int nullToMax(Integer v) {
        return v == null ? Integer.MAX_VALUE : v;
    }
}
