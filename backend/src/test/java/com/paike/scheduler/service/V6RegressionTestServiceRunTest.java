package com.paike.scheduler.service;

import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleRegressionTest;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.ScheduleRegressionTestMapper;
import com.paike.scheduler.service.vo.V6RegressionRunResultVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * A1：验证 V6RegressionTestService.run 的正式课表自检逻辑。
 * 重点验证 DB 唯一键拦不住的 ALL-vs-ODD/EVEN 周次重叠能被自检捕获。
 */
class V6RegressionTestServiceRunTest {

    @Test
    void run_detectsAllVsOddTeacherConflictOnSameSlot() {
        ScheduleRegressionTestMapper regressionTestMapper = mock(ScheduleRegressionTestMapper.class);
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        SemesterService semesterService = mock(SemesterService.class);
        Semester semester = new Semester();
        semester.setId(1L);
        when(semesterService.getCurrentSemester()).thenReturn(semester);
        // 同时段(10)同教师(5)：一条 ALL + 一条 ODD —— 唯一键不拦(week_type 不同值)，但语义重叠
        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(101L, 10L, 5L, "ALL"),
                schedule(102L, 10L, 5L, "ODD")));

        V6RegressionTestService service = new V6RegressionTestService(regressionTestMapper, scheduleMapper, semesterService);
        V6RegressionRunResultVo result = service.run(null);

        assertEquals(1L, result.getSemesterId());
        assertEquals(4, result.getTotal());
        assertTrue(result.getFailed() >= 1, "应至少有教师冲突自检项 FAIL");
        ScheduleRegressionTest teacherScan = result.getRecords().stream()
                .filter(r -> "TEACHER_CONFLICT_SCAN".equals(r.getTestCase()))
                .findFirst().orElse(null);
        assertNotNull(teacherScan);
        assertEquals("FAIL", teacherScan.getStatus());
        verify(regressionTestMapper, times(4)).insert(any(ScheduleRegressionTest.class));
    }

    @Test
    void run_passesWhenOddAndEvenShareSlot() {
        ScheduleRegressionTestMapper regressionTestMapper = mock(ScheduleRegressionTestMapper.class);
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        SemesterService semesterService = mock(SemesterService.class);
        Semester semester = new Semester();
        semester.setId(1L);
        when(semesterService.getCurrentSemester()).thenReturn(semester);
        // 同时段同教师/班级/教室：ODD + EVEN —— 单双周共槽合法，应全 PASS
        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(201L, 10L, 5L, "ODD"),
                schedule(202L, 10L, 5L, "EVEN")));

        V6RegressionTestService service = new V6RegressionTestService(regressionTestMapper, scheduleMapper, semesterService);
        V6RegressionRunResultVo result = service.run(null);

        assertEquals(4, result.getTotal());
        assertEquals(0, result.getFailed());
        assertEquals(4, result.getPassed());
    }

    // ============ V10 连续周段红线 ============

    /**
     * V10：ALL 1-8 与 ALL 9-16 同时段同资源 → 实际周集合不相交 → 全 PASS。
     */
    @Test
    void run_passesWhenDisjointWeekRangeShareSlot() {
        ScheduleRegressionTestMapper regressionTestMapper = mock(ScheduleRegressionTestMapper.class);
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        SemesterService semesterService = mock(SemesterService.class);
        Semester semester = new Semester();
        semester.setId(1L);
        when(semesterService.getCurrentSemester()).thenReturn(semester);
        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(301L, 10L, 5L, "ALL", 1, 8),
                schedule(302L, 10L, 5L, "ALL", 9, 16)));

        V6RegressionTestService service = new V6RegressionTestService(regressionTestMapper, scheduleMapper, semesterService);
        V6RegressionRunResultVo result = service.run(null);

        assertEquals(4, result.getTotal());
        assertEquals(0, result.getFailed(), "ALL 1-8 与 ALL 9-16 实际周集合不相交，应全 PASS");
        assertEquals(4, result.getPassed());
    }

    /**
     * V10：ALL 1-8 与 ODD 5-12 同时段同资源 → 重叠自然周 5、7 → 教师冲突自检 FAIL。
     */
    @Test
    void run_detectsOverlappingWeekRangeTeacherConflict() {
        ScheduleRegressionTestMapper regressionTestMapper = mock(ScheduleRegressionTestMapper.class);
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        SemesterService semesterService = mock(SemesterService.class);
        Semester semester = new Semester();
        semester.setId(1L);
        when(semesterService.getCurrentSemester()).thenReturn(semester);
        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(401L, 10L, 5L, "ALL", 1, 8),
                schedule(402L, 10L, 5L, "ODD", 5, 12)));

        V6RegressionTestService service = new V6RegressionTestService(regressionTestMapper, scheduleMapper, semesterService);
        V6RegressionRunResultVo result = service.run(null);

        assertTrue(result.getFailed() >= 1, "ALL 1-8 与 ODD 5-12 重叠自然周 5、7，应至少有教师冲突自检项 FAIL");
        ScheduleRegressionTest teacherScan = result.getRecords().stream()
                .filter(r -> "TEACHER_CONFLICT_SCAN".equals(r.getTestCase()))
                .findFirst().orElse(null);
        assertNotNull(teacherScan);
        assertEquals("FAIL", teacherScan.getStatus());
    }

    private Schedule schedule(Long id, Long timeSlotId, Long sharedResourceId, String weekType) {
        return schedule(id, timeSlotId, sharedResourceId, weekType, null, null);
    }

    /** V10 连续周段：带 startWeek/endWeek 的 schedule 构造 */
    private Schedule schedule(Long id, Long timeSlotId, Long sharedResourceId, String weekType,
                              Integer startWeek, Integer endWeek) {
        Schedule s = new Schedule();
        s.setId(id);
        s.setTimeSlotId(timeSlotId);
        s.setTeacherId(sharedResourceId);
        s.setClassId(sharedResourceId);
        s.setClassroomId(sharedResourceId);
        s.setTeachingTaskId(id);
        s.setWeekType(weekType);
        s.setStartWeek(startWeek);
        s.setEndWeek(endWeek);
        return s;
    }
}
