package com.paike.scheduler.service;

import com.paike.scheduler.controller.vo.TimetableVo;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V10 阶段 6 红线测试：导出/网格周段透传与标签展示。
 *
 * <p>验证：
 * <ol>
 *   <li><b>VO 透传</b>：TimetableVo 携带 startWeek/endWeek</li>
 *   <li><b>默认周段无标签</b>：ALL 1-20 导出 cell 无周段标记（零回归）</li>
 *   <li><b>非默认周段标签</b>：ALL 1-8 导出 cell 含 [1-8周]</li>
 *   <li><b>周段+单双周标签</b>：ODD 5-12 导出 cell 含 [5-12周/单]</li>
 *   <li><b>同 cell 多周段不覆盖</b>：ALL 1-8 + ALL 9-16 共槽，两条都展示</li>
 * </ol>
 */
class TimetableExportWeekRangeTest {

    /** VO 透传：list 返回的 TimetableVo.startWeek/endWeek 与 schedule 一致 */
    @Test
    void listTeacherTimetable_voCarriesWeekRange() {
        Schedule s1 = schedule(101L, 201L, "ALL", 1, 8);
        Schedule s2 = schedule(102L, 201L, "ODD", 5, 12);

        TimetableService service = newService(List.of(s1, s2),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L)),
                Map.of(301L, course(301L, "体育")),
                Map.of(401L, teacher(401L, "张老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1)),
                Map.of());

        List<TimetableVo> result = service.listTeacherTimetable(401L, 2L);

        assertEquals(2, result.size());
        TimetableVo all18 = result.stream().filter(v -> "ALL".equals(v.getWeekType())).findFirst().orElseThrow();
        TimetableVo odd512 = result.stream().filter(v -> "ODD".equals(v.getWeekType())).findFirst().orElseThrow();
        assertEquals(1, all18.getStartWeek());
        assertEquals(8, all18.getEndWeek());
        assertEquals(5, odd512.getStartWeek());
        assertEquals(12, odd512.getEndWeek());
    }

    /** 默认周段无标签：ALL 1-20 导出 cell 无任何周次标记（零回归） */
    @Test
    void exportDefaultWeekRange_noLabel() throws Exception {
        Schedule allSchedule = schedule(101L, 201L, "ALL", 1, 20);
        TimetableService service = newService(List.of(allSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L)),
                Map.of(301L, course(301L, "体育")),
                Map.of(401L, teacher(401L, "张老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1)),
                Map.of(601L, classroom(601L, "操场")));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        service.exportTeacherTimetable(401L, 2L, mockResponse(baos));

        String cellText = readCellText(baos, 2, 1);
        assertTrue(cellText.contains("体育"), "cell 应含课程名，实际: " + cellText);
        assertFalse(cellText.contains("["), "默认周段 ALL 1-20 不应有任何标记，实际: " + cellText);
    }

    /** 非默认周段标签：ALL 1-8 导出 cell 含 [1-8周] */
    @Test
    void exportNonDefaultWeekRange_showsRangeLabel() throws Exception {
        Schedule allSchedule = schedule(101L, 201L, "ALL", 1, 8);
        TimetableService service = newService(List.of(allSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L)),
                Map.of(301L, course(301L, "体育")),
                Map.of(401L, teacher(401L, "张老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1)),
                Map.of(601L, classroom(601L, "操场")));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        service.exportTeacherTimetable(401L, 2L, mockResponse(baos));

        String cellText = readCellText(baos, 2, 1);
        assertTrue(cellText.contains("体育"), "cell 应含课程名，实际: " + cellText);
        assertTrue(cellText.contains("[1-8周]"), "ALL 1-8 应显示 [1-8周]，实际: " + cellText);
        assertFalse(cellText.contains("单"), "ALL 不应含单双周标记，实际: " + cellText);
    }

    /** 周段+单双周标签：ODD 5-12 导出 cell 含 [5-12周/单] */
    @Test
    void exportWeekRangeWithOddType_showsRangeAndTypeLabel() throws Exception {
        Schedule oddSchedule = schedule(101L, 201L, "ODD", 5, 12);
        TimetableService service = newService(List.of(oddSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L)),
                Map.of(301L, course(301L, "体育")),
                Map.of(401L, teacher(401L, "张老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1)),
                Map.of(601L, classroom(601L, "操场")));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        service.exportTeacherTimetable(401L, 2L, mockResponse(baos));

        String cellText = readCellText(baos, 2, 1);
        assertTrue(cellText.contains("体育"), "cell 应含课程名，实际: " + cellText);
        assertTrue(cellText.contains("[5-12周/单]"), "ODD 5-12 应显示 [5-12周/单]，实际: " + cellText);
    }

    /** 同 cell 多周段不覆盖：ALL 1-8 + ALL 9-16 共槽，两条都展示，各自带周段标签 */
    @Test
    void exportDisjointWeekRanges_bothVisibleWithLabels() throws Exception {
        Schedule s1 = schedule(101L, 201L, "ALL", 1, 8);
        Schedule s2 = schedule(102L, 202L, "ALL", 9, 16);
        TimetableService service = newService(List.of(s1, s2),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L),
                        202L, teachingTask(202L, 302L, 401L, 501L, 601L)),
                Map.of(301L, course(301L, "体育"), 302L, course(302L, "思政")),
                Map.of(401L, teacher(401L, "张老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1), 202L, timeSlot(202L, 1, 1)),
                Map.of(601L, classroom(601L, "操场")));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        service.exportTeacherTimetable(401L, 2L, mockResponse(baos));

        String cellText = readCellText(baos, 2, 1);
        assertTrue(cellText.contains("体育"), "cell 应含体育课，实际: " + cellText);
        assertTrue(cellText.contains("思政"), "cell 应含思政课，实际: " + cellText);
        assertTrue(cellText.contains("[1-8周]"), "应含 [1-8周] 标签，实际: " + cellText);
        assertTrue(cellText.contains("[9-16周]"), "应含 [9-16周] 标签，实际: " + cellText);
    }

    // ============================================================
    // helpers（复用 TimetableExportWeekTypeTest 结构）
    // ============================================================

    private Schedule schedule(Long id, Long teachingTaskId, String weekType, int startWeek, int endWeek) {
        Schedule s = new Schedule();
        s.setId(id);
        s.setTeachingTaskId(teachingTaskId);
        s.setTimeSlotId(teachingTaskId);
        s.setWeekType(weekType);
        s.setStartWeek(startWeek);
        s.setEndWeek(endWeek);
        s.setClassroomId(601L);
        s.setSemesterId(2L);
        s.setTeacherId(401L);
        s.setClassId(501L);
        return s;
    }

    private TeachingTask teachingTask(Long id, Long courseId, Long teacherId, Long classId) {
        return teachingTask(id, courseId, teacherId, classId, null);
    }

    private TeachingTask teachingTask(Long id, Long courseId, Long teacherId, Long classId, Long classroomId) {
        TeachingTask t = new TeachingTask();
        t.setId(id);
        t.setCourseId(courseId);
        t.setTeacherId(teacherId);
        t.setClassId(classId);
        t.setSemesterId(2L);
        return t;
    }

    private Course course(Long id, String name) {
        Course c = new Course();
        c.setId(id);
        c.setCourseName(name);
        c.setCourseType("NORMAL");
        return c;
    }

    private Teacher teacher(Long id, String name) {
        Teacher t = new Teacher();
        t.setId(id);
        t.setName(name);
        t.setDeleted(0);
        return t;
    }

    private ClassInfo classInfo(Long id, String name) {
        ClassInfo c = new ClassInfo();
        c.setId(id);
        c.setClassName(name);
        c.setDeleted(0);
        return c;
    }

    private Classroom classroom(Long id, String name) {
        Classroom c = new Classroom();
        c.setId(id);
        c.setRoomName(name);
        c.setDeleted(0);
        return c;
    }

    private TimeSlot timeSlot(Long id, int dayOfWeek, int periodNo) {
        TimeSlot ts = new TimeSlot();
        ts.setId(id);
        ts.setDayOfWeek(dayOfWeek);
        ts.setPeriodNo(periodNo);
        ts.setTimeLabel("第" + (periodNo * 2 - 1) + "-" + (periodNo * 2) + "节");
        return ts;
    }

    private TimetableService newService(List<Schedule> schedules,
                                         Map<Long, TeachingTask> taskMap,
                                         Map<Long, Course> courseMap,
                                         Map<Long, Teacher> teacherMap,
                                         Map<Long, ClassInfo> classMap,
                                         Map<Long, TimeSlot> timeSlotMap,
                                         Map<Long, Classroom> classroomMap) {
        ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
        when(scheduleMapper.selectList(any())).thenReturn(schedules);
        TeachingTaskMapper taskMapper = mock(TeachingTaskMapper.class);
        configureBatchMap(taskMapper, taskMap);
        CourseMapper courseMapper = mock(CourseMapper.class);
        configureBatchMap(courseMapper, courseMap);
        TeacherMapper teacherMapper = mock(TeacherMapper.class);
        configureBatchMap(teacherMapper, teacherMap);
        ClassInfoMapper classInfoMapper = mock(ClassInfoMapper.class);
        configureBatchMap(classInfoMapper, classMap);
        TimeSlotMapper timeSlotMapper = mock(TimeSlotMapper.class);
        configureBatchMap(timeSlotMapper, timeSlotMap);
        ClassroomMapper classroomMapper = mock(ClassroomMapper.class);
        configureBatchMap(classroomMapper, classroomMap);
        return new TimetableService(scheduleMapper, taskMapper, timeSlotMapper, classroomMapper,
                courseMapper, teacherMapper, classInfoMapper, mock(SemesterService.class));
    }

    @SuppressWarnings("unchecked")
    private <E> void configureBatchMap(com.baomidou.mybatisplus.core.mapper.BaseMapper<E> mapper, Map<Long, E> map) {
        when(mapper.selectById(any())).thenAnswer(inv -> map.get(inv.getArgument(0)));
        when(mapper.selectBatchIds(any())).thenAnswer(inv -> {
            Object ids = inv.getArgument(0);
            java.util.List<E> result = new java.util.ArrayList<>();
            if (ids instanceof Iterable<?> iterable) {
                for (Object id : iterable) {
                    E entity = map.get(id);
                    if (entity != null) {
                        result.add(entity);
                    }
                }
            }
            return result;
        });
    }

    private HttpServletResponse mockResponse(ByteArrayOutputStream baos) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = new ServletOutputStream() {
            @Override
            public void write(int b) { baos.write(b); }
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) { }
        };
        when(response.getOutputStream()).thenReturn(sos);
        return response;
    }

    private String readCellText(ByteArrayOutputStream baos, int rowIdx, int colIdx) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                return "";
            }
            Cell cell = row.getCell(colIdx);
            return cell == null ? "" : cell.getStringCellValue();
        }
    }
}
