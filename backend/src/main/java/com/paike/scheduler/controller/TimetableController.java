package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.controller.vo.TimetableVo;
import com.paike.scheduler.service.TimetableService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;

    /** 班级课表 */
    @GetMapping("/classes/{classId}")
    public Result<List<TimetableVo>> classTimetable(@PathVariable Long classId) {
        return Result.success(timetableService.listClassTimetable(classId));
    }

    /** 教师课表 */
    @GetMapping("/teachers/{teacherId}")
    public Result<List<TimetableVo>> teacherTimetable(@PathVariable Long teacherId) {
        return Result.success(timetableService.listTeacherTimetable(teacherId));
    }

    /** 教室课表 */
    @GetMapping("/classrooms/{classroomId}")
    public Result<List<TimetableVo>> classroomTimetable(@PathVariable Long classroomId) {
        return Result.success(timetableService.listClassroomTimetable(classroomId));
    }

    /** 导出班级课表 */
    @GetMapping("/classes/{classId}/export")
    public void exportClassTimetable(@PathVariable Long classId, HttpServletResponse response) throws IOException {
        timetableService.exportClassTimetable(classId, response);
    }

    /** 导出教师课表 */
    @GetMapping("/teachers/{teacherId}/export")
    public void exportTeacherTimetable(@PathVariable Long teacherId, HttpServletResponse response) throws IOException {
        timetableService.exportTeacherTimetable(teacherId, response);
    }

    /** 导出教室占用表 */
    @GetMapping("/classrooms/{classroomId}/export")
    public void exportClassroomTimetable(@PathVariable Long classroomId, HttpServletResponse response) throws IOException {
        timetableService.exportClassroomTimetable(classroomId, response);
    }
}
