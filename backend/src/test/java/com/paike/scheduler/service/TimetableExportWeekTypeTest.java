package com.paike.scheduler.service;

import com.paike.scheduler.controller.vo.TimetableVo;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V9 阶段 2B T6：导出链 weekType 完整性测试（修复 R9 静默覆盖）。
 *
 * <p>覆盖四个验收点：
 * <ol>
 *   <li><b>VO 透传</b>：listTeacherTimetable 返回的 TimetableVo 含正确 weekType</li>
 *   <li><b>共槽不丢数据</b>：教师周一1-2节 ODD体育 + EVEN思政，导出 Excel 对应 cell 文本含两门课 + [单]/[双] 标记</li>
 *   <li><b>ALL 不加标记</b>：纯 ALL schedule 导出 cell 文本无 [单]/[双]/[全]</li>
 *   <li><b>三种视图</b>：teacher 视图 cell 含班级/教室（不含教师名，因高亮=teacher）</li>
 * </ol>
 *
 * <p>mock 范式参照 {@link TimetableServiceSemesterBoundaryTest}（8 依赖构造，mock mappers）。
 * 裁决依据：V9_00 R9（导出静默覆盖）、V9_05 T6。
 */
class TimetableExportWeekTypeTest {

    /** VO 透传：list 返回的 TimetableVo.weekType 与 schedule.weekType 一致 */
    @Test
    void listTeacherTimetable_voCarriesWeekType() {
        Schedule oddSchedule = schedule(101L, 201L, "ODD");
        Schedule evenSchedule = schedule(102L, 201L, "EVEN");

        TimetableService service = newService(List.of(oddSchedule, evenSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L)),
                Map.of(301L, course(301L, "体育")),
                Map.of(401L, teacher(401L, "张老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1)),
                Map.of());

        List<TimetableVo> result = service.listTeacherTimetable(401L, 2L);

        assertEquals(2, result.size());
        TimetableVo odd = result.stream().filter(v -> "ODD".equals(v.getWeekType())).findFirst().orElseThrow();
        TimetableVo even = result.stream().filter(v -> "EVEN".equals(v.getWeekType())).findFirst().orElseThrow();
        assertEquals("ODD", odd.getWeekType());
        assertEquals("EVEN", even.getWeekType());
    }

    /**
     * 共槽不丢数据（R9 核心）：教师周一1-2节 ODD体育 + EVEN思政，
     * 导出 Excel 对应 cell 含两门课名 + [单]/[双] 标记。
     */
    @Test
    void exportTeacherTimetable_oddEvenSharedSlotBothVisible() throws Exception {
        Schedule oddSchedule = schedule(101L, 201L, "ODD");
        Schedule evenSchedule = schedule(102L, 202L, "EVEN");
        // 两个 timeSlot 都映射到 周一 period=1（同 cell）
        ScheduleServiceFixture fix = new ScheduleServiceFixture(
                List.of(oddSchedule, evenSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L),
                        202L, teachingTask(202L, 302L, 401L, 501L, 601L)),
                Map.of(301L, course(301L, "体育"), 302L, course(302L, "思政")),
                Map.of(401L, teacher(401L, "张老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1), 202L, timeSlot(202L, 1, 1)),
                Map.of(601L, classroom(601L, "操场")));

        TimetableService service = fix.buildForExport();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        HttpServletResponse response = mockResponse(baos);

        service.exportTeacherTimetable(401L, 2L, response);

        String cellText = readCellText(baos, 2, 1); // sheetRow=2 (第一个 period 行), day=1 (周一)
        assertTrue(cellText.contains("体育"), "cell 应含体育课，实际: " + cellText);
        assertTrue(cellText.contains("思政"), "cell 应含思政课（不被覆盖），实际: " + cellText);
        assertTrue(cellText.contains("[单]"), "ODD 课应加 [单] 标记，实际: " + cellText);
        assertTrue(cellText.contains("[双]"), "EVEN 课应加 [双] 标记，实际: " + cellText);
    }

    /** ALL 不加标记：纯 ALL schedule 导出 cell 文本无 [单]/[双]/[全] */
    @Test
    void exportTeacherTimetable_allCourseNoMarker() throws Exception {
        Schedule allSchedule = schedule(101L, 201L, "ALL");
        ScheduleServiceFixture fix = new ScheduleServiceFixture(
                List.of(allSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L)),
                Map.of(301L, course(301L, "体育")),
                Map.of(401L, teacher(401L, "张老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1)),
                Map.of(601L, classroom(601L, "操场")));

        TimetableService service = fix.buildForExport();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        HttpServletResponse response = mockResponse(baos);

        service.exportTeacherTimetable(401L, 2L, response);

        String cellText = readCellText(baos, 2, 1);
        assertTrue(cellText.contains("体育"), "cell 应含体育课，实际: " + cellText);
        assertFalse(cellText.contains("["), "ALL 课不应有任何周次标记，实际: " + cellText);
    }

    /**
     * teacher 视图 cell 内容：课程名 + 班级 + 教室（不含教师名，因高亮=teacher 时教师名是主体已知）。
     * 验证 viewType 影响拼接的次要信息。
     */
    @Test
    void exportTeacherTimetable_viewTypeShowsClassAndRoom() throws Exception {
        Schedule allSchedule = schedule(101L, 201L, "ALL");
        ScheduleServiceFixture fix = new ScheduleServiceFixture(
                List.of(allSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L)),
                Map.of(301L, course(301L, "体育")),
                Map.of(401L, teacher(401L, "张老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1)),
                Map.of(601L, classroom(601L, "操场")));

        TimetableService service = fix.buildForExport();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        HttpServletResponse response = mockResponse(baos);

        service.exportTeacherTimetable(401L, 2L, response);

        String cellText = readCellText(baos, 2, 1);
        assertTrue(cellText.contains("高三1班"), "teacher 视图应含班级名，实际: " + cellText);
        assertTrue(cellText.contains("操场"), "teacher 视图应含教室名，实际: " + cellText);
    }

    // ============================================================
    // C3 补齐：CLASS 视图与 CLASSROOM 视图的 weekType 导出覆盖
    // 原测试 4 个用例全部针对 TEACHER 视图；CLASS/CLASSROOM 零覆盖。
    // 实现层三视图共用同一 buildCellText，viewType 仅影响 switch 分支的次要信息，
    // 但“实现一致”不等于“已验证不变”——补测试防止未来 switch 分支被改后静默回归。
    // ============================================================

    /**
     * CLASS 视图共槽：班级周一1-2节 ODD体育 + EVEN思政，
     * 导出 cell 含两门课 + [单]/[双] 标记，且次要信息为教师名 + 教室名（不含班级名，因高亮=class）。
     */
    @Test
    void exportClassTimetable_oddEvenSharedSlotBothVisible() throws Exception {
        Schedule oddSchedule = schedule(101L, 201L, "ODD");
        Schedule evenSchedule = schedule(102L, 202L, "EVEN");
        ScheduleServiceFixture fix = new ScheduleServiceFixture(
                List.of(oddSchedule, evenSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L),
                        202L, teachingTask(202L, 302L, 402L, 501L, 601L)),
                Map.of(301L, course(301L, "体育"), 302L, course(302L, "思政")),
                Map.of(401L, teacher(401L, "张老师"), 402L, teacher(402L, "李老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1), 202L, timeSlot(202L, 1, 1)),
                Map.of(601L, classroom(601L, "操场")));

        TimetableService service = fix.buildForExport();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        HttpServletResponse response = mockResponse(baos);

        service.exportClassTimetable(501L, 2L, response);

        String cellText = readCellText(baos, 2, 1);
        assertTrue(cellText.contains("体育"), "CLASS 视图 cell 应含体育课，实际: " + cellText);
        assertTrue(cellText.contains("思政"), "CLASS 视图 cell 应含思政课（不被覆盖），实际: " + cellText);
        assertTrue(cellText.contains("[单]"), "CLASS 视图 ODD 课应加 [单] 标记，实际: " + cellText);
        assertTrue(cellText.contains("[双]"), "CLASS 视图 EVEN 课应加 [双] 标记，实际: " + cellText);
        assertTrue(cellText.contains("张老师"), "CLASS 视图应含教师名，实际: " + cellText);
        assertTrue(cellText.contains("操场"), "CLASS 视图应含教室名，实际: " + cellText);
        assertFalse(cellText.contains("高三1班"), "CLASS 视图不应含班级名（高亮=class），实际: " + cellText);
    }

    /**
     * CLASSROOM 视图共槽：教室周一1-2节 ODD体育 + EVEN思政，
     * 导出 cell 含两门课 + [单]/[双] 标记，且次要信息为教师名 + 班级名（不含教室名，因高亮=classroom）。
     */
    @Test
    void exportClassroomTimetable_oddEvenSharedSlotBothVisible() throws Exception {
        Schedule oddSchedule = schedule(101L, 201L, "ODD");
        Schedule evenSchedule = schedule(102L, 202L, "EVEN");
        ScheduleServiceFixture fix = new ScheduleServiceFixture(
                List.of(oddSchedule, evenSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L),
                        202L, teachingTask(202L, 302L, 402L, 502L, 601L)),
                Map.of(301L, course(301L, "体育"), 302L, course(302L, "思政")),
                Map.of(401L, teacher(401L, "张老师"), 402L, teacher(402L, "李老师")),
                Map.of(501L, classInfo(501L, "高三1班"), 502L, classInfo(502L, "高三2班")),
                Map.of(201L, timeSlot(201L, 1, 1), 202L, timeSlot(202L, 1, 1)),
                Map.of(601L, classroom(601L, "操场")));

        TimetableService service = fix.buildForExport();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        HttpServletResponse response = mockResponse(baos);

        service.exportClassroomTimetable(601L, 2L, response);

        String cellText = readCellText(baos, 2, 1);
        assertTrue(cellText.contains("体育"), "CLASSROOM 视图 cell 应含体育课，实际: " + cellText);
        assertTrue(cellText.contains("思政"), "CLASSROOM 视图 cell 应含思政课（不被覆盖），实际: " + cellText);
        assertTrue(cellText.contains("[单]"), "CLASSROOM 视图 ODD 课应加 [单] 标记，实际: " + cellText);
        assertTrue(cellText.contains("[双]"), "CLASSROOM 视图 EVEN 课应加 [双] 标记，实际: " + cellText);
        assertTrue(cellText.contains("张老师"), "CLASSROOM 视图应含教师名，实际: " + cellText);
        assertTrue(cellText.contains("高三1班"), "CLASSROOM 视图应含班级名，实际: " + cellText);
        assertFalse(cellText.contains("操场"), "CLASSROOM 视图不应含教室名（高亮=classroom），实际: " + cellText);
    }

    /**
     * CLASS 视图 ALL 课无标记：与 TEACHER 视图对称，确认 ALL 不加 [单]/[双]/[全]。
     */
    @Test
    void exportClassTimetable_allCourseNoMarker() throws Exception {
        Schedule allSchedule = schedule(101L, 201L, "ALL");
        ScheduleServiceFixture fix = new ScheduleServiceFixture(
                List.of(allSchedule),
                Map.of(201L, teachingTask(201L, 301L, 401L, 501L, 601L)),
                Map.of(301L, course(301L, "数学")),
                Map.of(401L, teacher(401L, "王老师")),
                Map.of(501L, classInfo(501L, "高三1班")),
                Map.of(201L, timeSlot(201L, 1, 1)),
                Map.of(601L, classroom(601L, "A101")));

        TimetableService service = fix.buildForExport();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        HttpServletResponse response = mockResponse(baos);

        service.exportClassTimetable(501L, 2L, response);

        String cellText = readCellText(baos, 2, 1);
        assertTrue(cellText.contains("数学"), "CLASS 视图 cell 应含数学课，实际: " + cellText);
        assertFalse(cellText.contains("["), "CLASS 视图 ALL 课不应有任何周次标记，实际: " + cellText);
    }

    // ============================================================
    // helpers：实体构造
    // ============================================================

    private Schedule schedule(Long id, Long teachingTaskId, String weekType) {
        Schedule s = new Schedule();
        s.setId(id);
        s.setTeachingTaskId(teachingTaskId);
        s.setTimeSlotId(teachingTaskId); // 简化：timeSlotId 复用 taskId，测试 fixture 单独映射
        s.setWeekType(weekType);
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

    /**
     * list/export 路径用的 service。
     * toTimetableVos 用 selectBatchIds（批量），export 的 selectById 校验用 selectById（单条），
     * 故每个实体 mapper 同时 mock 两个方法。
     */
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

    /** 同时 mock selectById（单条）和 selectBatchIds（批量），从 map 取实体 */
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

    /** 读取导出 Excel 指定 (row, col) cell 文本 */
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

    /**
     * Fixture：集中持有 7 个 mock mapper 的构造状态，buildForExport 额外满足
     * exportTeacherTimetable 对 teacherMapper.selectById(401L) 的校验。
     */
    private class ScheduleServiceFixture {
        final List<Schedule> schedules;
        final Map<Long, TeachingTask> taskMap;
        final Map<Long, Course> courseMap;
        final Map<Long, Teacher> teacherMap;
        final Map<Long, ClassInfo> classMap;
        final Map<Long, TimeSlot> timeSlotMap;
        final Map<Long, Classroom> classroomMap;

        ScheduleServiceFixture(List<Schedule> schedules, Map<Long, TeachingTask> taskMap,
                               Map<Long, Course> courseMap, Map<Long, Teacher> teacherMap,
                               Map<Long, ClassInfo> classMap, Map<Long, TimeSlot> timeSlotMap,
                               Map<Long, Classroom> classroomMap) {
            this.schedules = schedules;
            this.taskMap = taskMap;
            this.courseMap = courseMap;
            this.teacherMap = teacherMap;
            this.classMap = classMap;
            this.timeSlotMap = timeSlotMap;
            this.classroomMap = classroomMap;
        }

        TimetableService buildForExport() {
            return newService(schedules, taskMap, courseMap, teacherMap, classMap, timeSlotMap, classroomMap);
        }
    }
}
