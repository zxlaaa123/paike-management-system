package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeachingTask;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.controller.vo.TimetableVo;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeachingTaskMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class TimetableController {

    private static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final List<PeriodDefinition> PERIODS = List.of(
        new PeriodDefinition(1, "第1-2节"),
        new PeriodDefinition(2, "第3-4节"),
        new PeriodDefinition(3, "第5-6节"),
        new PeriodDefinition(4, "第7-8节")
    );
    private static final List<String> DAY_NAMES = List.of("周一", "周二", "周三", "周四", "周五");

    private final ScheduleMapper scheduleMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;

    /** 班级课表 */
    @GetMapping("/classes/{classId}")
    public Result<List<TimetableVo>> classTimetable(@PathVariable Long classId) {
        List<Schedule> schedules = queryByClassId(classId);
        return Result.success(toTimetableVos(schedules));
    }

    /** 教师课表 */
    @GetMapping("/teachers/{teacherId}")
    public Result<List<TimetableVo>> teacherTimetable(@PathVariable Long teacherId) {
        List<Schedule> schedules = queryByTeacherId(teacherId);
        return Result.success(toTimetableVos(schedules));
    }

    /** 教室课表 */
    @GetMapping("/classrooms/{classroomId}")
    public Result<List<TimetableVo>> classroomTimetable(@PathVariable Long classroomId) {
        List<Schedule> schedules = queryByClassroomId(classroomId);
        return Result.success(toTimetableVos(schedules));
    }

    /** 导出班级课表 */
    @GetMapping("/classes/{classId}/export")
    public void exportClassTimetable(@PathVariable Long classId, HttpServletResponse response) throws IOException {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null || Integer.valueOf(1).equals(classInfo.getDeleted())) {
            throw new BusinessException(404, "班级不存在");
        }
        List<TimetableVo> items = toTimetableVos(queryByClassId(classId));
        exportWorkbook(response, buildFileName(classInfo.getClassName(), "班级课表"), classInfo.getClassName() + "课表", items, TimetableViewType.CLASS);
    }

    /** 导出教师课表 */
    @GetMapping("/teachers/{teacherId}/export")
    public void exportTeacherTimetable(@PathVariable Long teacherId, HttpServletResponse response) throws IOException {
        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || Integer.valueOf(1).equals(teacher.getDeleted())) {
            throw new BusinessException(404, "教师不存在");
        }
        List<TimetableVo> items = toTimetableVos(queryByTeacherId(teacherId));
        exportWorkbook(response, buildFileName(teacher.getName(), "教师课表"), teacher.getName() + "课表", items, TimetableViewType.TEACHER);
    }

    /** 导出教室占用表 */
    @GetMapping("/classrooms/{classroomId}/export")
    public void exportClassroomTimetable(@PathVariable Long classroomId, HttpServletResponse response) throws IOException {
        Classroom classroom = classroomMapper.selectById(classroomId);
        if (classroom == null || Integer.valueOf(1).equals(classroom.getDeleted())) {
            throw new BusinessException(404, "教室不存在");
        }
        List<TimetableVo> items = toTimetableVos(queryByClassroomId(classroomId));
        exportWorkbook(response, buildFileName(classroom.getRoomName(), "教室占用表"), classroom.getRoomName() + "占用表", items, TimetableViewType.CLASSROOM);
    }

    /**
     * 通用查询：先按 schedule 表字段查，再通过 teaching_task 关联查，合并去重
     */
    private List<Schedule> querySchedulesByTaskField(
            String taskField, Long fieldValue,
            java.util.function.Function<LambdaQueryWrapper<Schedule>, LambdaQueryWrapper<Schedule>> scheduleFilter) {
        // 通过 teaching_task 关联查询
        LambdaQueryWrapper<TeachingTask> taskWrapper = new LambdaQueryWrapper<TeachingTask>();
        switch (taskField) {
            case "classId" -> taskWrapper.eq(TeachingTask::getClassId, fieldValue);
            case "teacherId" -> taskWrapper.eq(TeachingTask::getTeacherId, fieldValue);
        }
        List<TeachingTask> tasks = teachingTaskMapper.selectList(taskWrapper);
        List<Long> taskIds = tasks.stream().map(TeachingTask::getId).collect(Collectors.toList());

        // 直接按 schedule 表字段查
        LambdaQueryWrapper<Schedule> scheduleWrapper = new LambdaQueryWrapper<Schedule>();
        scheduleFilter.apply(scheduleWrapper);
        List<Schedule> schedules = scheduleMapper.selectList(scheduleWrapper);

        // 合并去重
        if (!taskIds.isEmpty()) {
            List<Schedule> taskSchedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>()
                    .in(Schedule::getTeachingTaskId, taskIds)
            );
            Set<Long> existingIds = schedules.stream().map(Schedule::getId).collect(Collectors.toSet());
            for (Schedule s : taskSchedules) {
                if (!existingIds.contains(s.getId())) {
                    schedules.add(s);
                }
            }
        }
        return schedules;
    }

    private List<Schedule> queryByClassId(Long classId) {
        return querySchedulesByTaskField("classId", classId,
            w -> w.eq(Schedule::getClassId, classId));
    }

    private List<Schedule> queryByTeacherId(Long teacherId) {
        return querySchedulesByTaskField("teacherId", teacherId,
            w -> w.eq(Schedule::getTeacherId, teacherId));
    }

    private List<Schedule> queryByClassroomId(Long classroomId) {
        return scheduleMapper.selectList(
            new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getClassroomId, classroomId)
        );
    }

    private List<TimetableVo> toTimetableVos(List<Schedule> schedules) {
        if (schedules.isEmpty()) return List.of();

        // 收集所有需要查询的ID
        List<Long> timeSlotIds = schedules.stream().map(Schedule::getTimeSlotId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> classroomIds = schedules.stream().map(Schedule::getClassroomId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> taskIds = schedules.stream().map(Schedule::getTeachingTaskId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // 批量查询关联数据
        Map<Long, TimeSlot> timeSlotMap = timeSlotIds.isEmpty() ? Map.of() :
            timeSlotMapper.selectBatchIds(timeSlotIds).stream().collect(Collectors.toMap(TimeSlot::getId, Function.identity(), (a, b) -> a));
        Map<Long, Classroom> classroomMap = classroomIds.isEmpty() ? Map.of() :
            classroomMapper.selectBatchIds(classroomIds).stream().collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));
        Map<Long, TeachingTask> taskMap = taskIds.isEmpty() ? Map.of() :
            teachingTaskMapper.selectBatchIds(taskIds).stream().collect(Collectors.toMap(TeachingTask::getId, Function.identity(), (a, b) -> a));

        // 收集教学任务关联的课程/教师/班级ID
        List<Long> courseIds = new ArrayList<>();
        List<Long> teacherIds = new ArrayList<>();
        List<Long> classIds = new ArrayList<>();
        for (TeachingTask task : taskMap.values()) {
            if (task.getCourseId() != null) courseIds.add(task.getCourseId());
            if (task.getTeacherId() != null) teacherIds.add(task.getTeacherId());
            if (task.getClassId() != null) classIds.add(task.getClassId());
        }

        Map<Long, Course> courseMap = courseIds.isEmpty() ? Map.of() :
            courseMapper.selectBatchIds(courseIds).stream().collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherIds.isEmpty() ? Map.of() :
            teacherMapper.selectBatchIds(teacherIds).stream().collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        Map<Long, ClassInfo> classMap = classIds.isEmpty() ? Map.of() :
            classInfoMapper.selectBatchIds(classIds).stream().collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));

        return schedules.stream()
            .map(s -> buildTimetableVo(s, timeSlotMap, classroomMap, taskMap, courseMap, teacherMap, classMap))
            .sorted(Comparator
                .comparing(TimetableVo::getDayOfWeek, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TimetableVo::getPeriod, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TimetableVo::getScheduleId, Comparator.nullsLast(Long::compareTo)))
            .collect(Collectors.toList());
    }

    private TimetableVo buildTimetableVo(Schedule schedule, Map<Long, TimeSlot> timeSlotMap,
                                          Map<Long, Classroom> classroomMap, Map<Long, TeachingTask> taskMap,
                                          Map<Long, Course> courseMap, Map<Long, Teacher> teacherMap,
                                          Map<Long, ClassInfo> classMap) {
        TimetableVo vo = new TimetableVo();
        vo.setScheduleId(schedule.getId());

        TimeSlot timeSlot = timeSlotMap.get(schedule.getTimeSlotId());
        if (timeSlot != null) {
            vo.setTimeSlotId(timeSlot.getId());
            vo.setDayOfWeek(timeSlot.getDayOfWeek());
            vo.setPeriod(timeSlot.getPeriodNo());
            vo.setTimeSlotName(timeSlot.getTimeLabel());
        }

        Classroom classroom = classroomMap.get(schedule.getClassroomId());
        if (classroom != null) {
            vo.setClassroomName(classroom.getRoomName());
            vo.setBuilding(classroom.getBuilding());
        }

        TeachingTask task = taskMap.get(schedule.getTeachingTaskId());
        if (task != null) {
            Course course = courseMap.get(task.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getCourseName());
                vo.setCourseType(course.getCourseType());
            }
            Teacher teacher = teacherMap.get(task.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getName());
            }
            ClassInfo classInfo = classMap.get(task.getClassId());
            if (classInfo != null) {
                vo.setClassName(classInfo.getClassName());
            }
        }
        return vo;
    }

    private void exportWorkbook(HttpServletResponse response, String fileName, String sheetName, List<TimetableVo> items, TimetableViewType viewType) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(sheetName);
            createTimetableSheet(workbook, sheet, items, viewType, sheetName);
            response.setContentType(CONTENT_TYPE_XLSX);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodeFileName(fileName));
            try (ServletOutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                outputStream.flush();
            }
        }
    }

    private void createTimetableSheet(XSSFWorkbook workbook, XSSFSheet sheet, List<TimetableVo> items, TimetableViewType viewType, String title) {
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle periodStyle = createPeriodStyle(workbook);
        CellStyle cellStyle = createCellStyle(workbook);

        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        Cell titleCell = getOrCreateCell(sheet, 0, 0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);

        Cell emptyHeader = getOrCreateCell(sheet, 1, 0);
        emptyHeader.setCellStyle(headerStyle);
        for (int day = 1; day <= DAY_NAMES.size(); day++) {
            Cell cell = getOrCreateCell(sheet, 1, day);
            cell.setCellValue(DAY_NAMES.get(day - 1));
            cell.setCellStyle(headerStyle);
        }

        Map<String, TimetableVo> itemMap = items.stream().collect(Collectors.toMap(
            item -> buildCellKey(item.getDayOfWeek(), item.getPeriod()),
            item -> item,
            (first, second) -> first,
            LinkedHashMap::new
        ));

        for (int rowIndex = 0; rowIndex < PERIODS.size(); rowIndex++) {
            PeriodDefinition period = PERIODS.get(rowIndex);
            int sheetRow = rowIndex + 2;
            Cell periodCell = getOrCreateCell(sheet, sheetRow, 0);
            periodCell.setCellValue(period.label());
            periodCell.setCellStyle(periodStyle);

            for (int day = 1; day <= DAY_NAMES.size(); day++) {
                Cell cell = getOrCreateCell(sheet, sheetRow, day);
                TimetableVo item = itemMap.get(buildCellKey(day, period.index()));
                cell.setCellValue(item == null ? "" : buildCellText(item, viewType));
                cell.setCellStyle(cellStyle);
            }
        }

        sheet.setColumnWidth(0, 14 * 256);
        for (int column = 1; column <= 5; column++) {
            sheet.setColumnWidth(column, 20 * 256);
        }
        sheet.getRow(0).setHeightInPoints(26);
        for (int row = 2; row <= PERIODS.size() + 1; row++) {
            sheet.getRow(row).setHeightInPoints(42);
        }
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorder(style);
        var font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 13);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorder(style);
        var font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createPeriodStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(style);
        var font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        applyBorder(style);
        return style;
    }

    private void applyBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private Cell getOrCreateCell(XSSFSheet sheet, int rowIndex, int colIndex) {
        var row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        var cell = row.getCell(colIndex);
        return cell != null ? cell : row.createCell(colIndex);
    }

    private String buildFileName(String objectName, String suffix) {
        return sanitizeFileName(objectName) + "_" + suffix + "_" + LocalDate.now().format(FILE_DATE_FORMATTER) + ".xlsx";
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "课表";
        }
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    private String buildCellKey(Integer dayOfWeek, Integer period) {
        return dayOfWeek + "_" + period;
    }

    private String buildCellText(TimetableVo item, TimetableViewType viewType) {
        List<String> lines = new ArrayList<>();
        lines.add(defaultString(item.getCourseName()));
        switch (viewType) {
            case CLASS -> {
                appendIfPresent(lines, item.getTeacherName());
                appendIfPresent(lines, item.getClassroomName());
            }
            case TEACHER -> {
                appendIfPresent(lines, item.getClassName());
                appendIfPresent(lines, item.getClassroomName());
            }
            case CLASSROOM -> {
                appendIfPresent(lines, item.getTeacherName());
                appendIfPresent(lines, item.getClassName());
            }
        }
        return lines.stream()
            .filter(line -> line != null && !line.isBlank())
            .collect(Collectors.joining("\n"));
    }

    private void appendIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private enum TimetableViewType {
        CLASS,
        TEACHER,
        CLASSROOM
    }

    private record PeriodDefinition(int index, String label) {
    }
}
