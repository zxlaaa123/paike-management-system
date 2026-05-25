package com.paike.scheduler.service.scheduling;

import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.TeacherUnavailableTime;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulingSupportTest {

    @Test
    void isRoomTypeMatched_experimentRequiresLab() {
        assertTrue(SchedulingSupport.isRoomTypeMatched("EXPERIMENT", "LAB"));
        assertFalse(SchedulingSupport.isRoomTypeMatched("EXPERIMENT", "COMPUTER"));
        assertFalse(SchedulingSupport.isRoomTypeMatched("EXPERIMENT", "NORMAL"));
    }

    @Test
    void isRoomTypeMatched_computerRequiresComputerRoom() {
        assertTrue(SchedulingSupport.isRoomTypeMatched("COMPUTER", "COMPUTER"));
        assertFalse(SchedulingSupport.isRoomTypeMatched("COMPUTER", "LAB"));
        assertFalse(SchedulingSupport.isRoomTypeMatched("COMPUTER", "NORMAL"));
    }

    @Test
    void isRoomTypeMatched_normalCourseAllowsAnyRoom() {
        assertTrue(SchedulingSupport.isRoomTypeMatched("NORMAL", "NORMAL"));
        assertTrue(SchedulingSupport.isRoomTypeMatched("NORMAL", "LAB"));
        assertTrue(SchedulingSupport.isRoomTypeMatched("NORMAL", "COMPUTER"));
    }

    @Test
    void getCourseType_fallbackToNormalWhenMissing() {
        Course course = new Course();
        course.setId(1L);
        course.setCourseType("EXPERIMENT");
        Map<Long, Course> map = Map.of(1L, course);

        assertEquals("EXPERIMENT", SchedulingSupport.getCourseType(1L, map));
        assertEquals("NORMAL", SchedulingSupport.getCourseType(2L, map));
    }

    @Test
    void getClassStudentCount_handlesNullSafely() {
        ClassInfo info = new ClassInfo();
        info.setId(1L);
        info.setStudentCount(45);
        ClassInfo nullCountInfo = new ClassInfo();
        nullCountInfo.setId(2L);
        nullCountInfo.setStudentCount(null);
        Map<Long, ClassInfo> map = Map.of(1L, info, 2L, nullCountInfo);

        assertEquals(45, SchedulingSupport.getClassStudentCount(1L, map));
        assertEquals(0, SchedulingSupport.getClassStudentCount(2L, map));
        assertEquals(0, SchedulingSupport.getClassStudentCount(999L, map));
    }

    @Test
    void sortTasks_experimentBeforeNormalEvenIfFewerHours() {
        Course normal = newCourse(1L, "NORMAL");
        Course experiment = newCourse(2L, "EXPERIMENT");
        ClassInfo c1 = newClass(10L, 30);

        TeachingTask normalTask = newTask(100L, 1L, 10L, 1L, 6);
        TeachingTask experimentTask = newTask(101L, 2L, 10L, 1L, 2);

        List<TeachingTask> sorted = SchedulingSupport.sortTasks(
                List.of(normalTask, experimentTask),
                Map.of(),
                Map.of(1L, normal, 2L, experiment),
                Map.of(10L, c1));

        assertEquals(101L, sorted.get(0).getId());
        assertEquals(100L, sorted.get(1).getId());
    }

    @Test
    void sortTasks_byStudentCountWhenSameType() {
        Course normal = newCourse(1L, "NORMAL");
        ClassInfo small = newClass(10L, 20);
        ClassInfo big = newClass(11L, 50);

        TeachingTask smallTask = newTask(100L, 1L, 10L, 1L, 4);
        TeachingTask bigTask = newTask(101L, 1L, 11L, 1L, 4);

        List<TeachingTask> sorted = SchedulingSupport.sortTasks(
                List.of(smallTask, bigTask),
                Map.of(),
                Map.of(1L, normal),
                Map.of(10L, small, 11L, big));

        assertEquals(101L, sorted.get(0).getId());
    }

    @Test
    void sortTasks_byWeeklyHoursWhenTypeAndCountTie() {
        Course normal = newCourse(1L, "NORMAL");
        ClassInfo info = newClass(10L, 30);

        TeachingTask few = newTask(100L, 1L, 10L, 1L, 2);
        TeachingTask many = newTask(101L, 1L, 10L, 1L, 6);

        List<TeachingTask> sorted = SchedulingSupport.sortTasks(
                List.of(few, many),
                Map.of(),
                Map.of(1L, normal),
                Map.of(10L, info));

        assertEquals(101L, sorted.get(0).getId());
    }

    @Test
    void sortTimeSlots_morningFirstWhenEnabled() {
        TimeSlot morning = newSlot(1L, 1, 1, 1);
        TimeSlot afternoon = newSlot(2L, 1, 3, 2);

        List<TimeSlot> sorted = SchedulingSupport.sortTimeSlots(
                List.of(afternoon, morning), true, false);

        assertEquals(1L, sorted.get(0).getId());
    }

    @Test
    void sortTimeSlots_fridayAfternoonLastWhenAvoided() {
        TimeSlot mondayAfternoon = newSlot(1L, 1, 3, 1);
        TimeSlot fridayAfternoon = newSlot(2L, 5, 3, 2);

        List<TimeSlot> sorted = SchedulingSupport.sortTimeSlots(
                List.of(fridayAfternoon, mondayAfternoon), false, true);

        assertEquals(1L, sorted.get(0).getId());
        assertEquals(2L, sorted.get(1).getId());
    }

    @Test
    void sortTimeSlots_nullFieldsGoLast() {
        TimeSlot good = newSlot(1L, 1, 1, 1);
        TimeSlot bad = new TimeSlot();
        bad.setId(2L);

        List<TimeSlot> sorted = SchedulingSupport.sortTimeSlots(
                List.of(bad, good), false, false);

        assertEquals(1L, sorted.get(0).getId());
    }

    @Test
    void toUnavailableKeySet_concatsTeacherAndSlot() {
        TeacherUnavailableTime t1 = newUnavailable(1L, 10L);
        TeacherUnavailableTime t2 = newUnavailable(1L, 11L);
        TeacherUnavailableTime t3 = newUnavailable(2L, 10L);

        Set<String> keys = SchedulingSupport.toUnavailableKeySet(List.of(t1, t2, t3));

        assertEquals(3, keys.size());
        assertTrue(keys.contains("1_10"));
        assertTrue(keys.contains("1_11"));
        assertTrue(keys.contains("2_10"));
    }

    @Test
    void toUnavailableCountByTeacher_groupsByTeacherId() {
        TeacherUnavailableTime a1 = newUnavailable(1L, 10L);
        TeacherUnavailableTime a2 = newUnavailable(1L, 11L);
        TeacherUnavailableTime b1 = newUnavailable(2L, 10L);

        Map<Long, Long> counts = SchedulingSupport.toUnavailableCountByTeacher(List.of(a1, a2, b1));

        assertEquals(2L, counts.get(1L));
        assertEquals(1L, counts.get(2L));
    }

    @Test
    void slotIdsByDay_groupsAndSkipsNullDay() {
        TimeSlot mon1 = newSlot(1L, 1, 1, 1);
        TimeSlot mon2 = newSlot(2L, 1, 2, 2);
        TimeSlot tue1 = newSlot(3L, 2, 1, 3);
        TimeSlot orphan = new TimeSlot();
        orphan.setId(4L);
        orphan.setDayOfWeek(null);

        Map<Integer, List<Long>> grouped =
                SchedulingSupport.slotIdsByDay(List.of(mon1, mon2, tue1, orphan));

        assertEquals(List.of(1L, 2L), grouped.get(1));
        assertEquals(List.of(3L), grouped.get(2));
        assertFalse(grouped.containsKey(null));
    }

    private static TeachingTask newTask(Long id, Long courseId, Long classId, Long teacherId, Integer weeklyHours) {
        TeachingTask task = new TeachingTask();
        task.setId(id);
        task.setCourseId(courseId);
        task.setClassId(classId);
        task.setTeacherId(teacherId);
        task.setWeeklyHours(weeklyHours);
        return task;
    }

    private static Course newCourse(Long id, String type) {
        Course course = new Course();
        course.setId(id);
        course.setCourseType(type);
        return course;
    }

    private static ClassInfo newClass(Long id, Integer count) {
        ClassInfo info = new ClassInfo();
        info.setId(id);
        info.setStudentCount(count);
        return info;
    }

    private static TimeSlot newSlot(Long id, Integer day, Integer period, Integer sortOrder) {
        TimeSlot slot = new TimeSlot();
        slot.setId(id);
        slot.setDayOfWeek(day);
        slot.setPeriodNo(period);
        slot.setSortOrder(sortOrder);
        return slot;
    }

    private static TeacherUnavailableTime newUnavailable(Long teacherId, Long slotId) {
        TeacherUnavailableTime t = new TeacherUnavailableTime();
        t.setTeacherId(teacherId);
        t.setTimeSlotId(slotId);
        return t;
    }
}
