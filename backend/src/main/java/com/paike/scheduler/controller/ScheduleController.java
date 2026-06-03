package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.controller.vo.ConflictCheckResultVo;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.service.ScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public Result<Page<Schedule>> list(
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) String teacherName,
        @RequestParam(required = false) String className,
        @RequestParam(required = false) String roomName,
        @RequestParam(required = false) Integer dayOfWeek,
        @RequestParam(required = false) Long semesterId,
        @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
        @RequestParam(defaultValue = "1") int page,
        @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
        @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
        @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(scheduleService.list(courseName, teacherName, className, roomName, dayOfWeek, semesterId, page, size));
    }

    @GetMapping("/{id}")
    public Result<Schedule> getById(@PathVariable Long id) {
        return Result.success(scheduleService.getById(id));
    }

    @PostMapping
    public Result<Schedule> create(@Valid @RequestBody ScheduleForm form) {
        return Result.success(scheduleService.create(
            form.getTeachingTaskId(),
            form.getTimeSlotId(),
            form.getClassroomId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.success("删除成功", null);
    }

    /** 按班级查询排课列表 */
    @GetMapping("/class/{classId}")
    public Result<List<Schedule>> listByClass(@PathVariable Long classId) {
        return Result.success(scheduleService.listByClass(classId));
    }

    /** 按教师查询排课列表 */
    @GetMapping("/teacher/{teacherId}")
    public Result<List<Schedule>> listByTeacher(@PathVariable Long teacherId) {
        return Result.success(scheduleService.listByTeacher(teacherId));
    }

    /** 按教室查询排课列表 */
    @GetMapping("/classroom/{classroomId}")
    public Result<List<Schedule>> listByClassroom(@PathVariable Long classroomId) {
        return Result.success(scheduleService.listByClassroom(classroomId));
    }

    /** 冲突检测接口（前端预检用，保存时仍会再次检测） */
    @PostMapping("/check-conflict")
    public Result<ConflictCheckResultVo> checkConflict(@Valid @RequestBody ScheduleForm form) {
        String conflict = scheduleService.checkConflict(
            form.getTeachingTaskId(),
            form.getTimeSlotId(),
            form.getClassroomId());
        if (conflict != null) {
            return Result.success(new ConflictCheckResultVo(true, conflict));
        }
        return Result.success(new ConflictCheckResultVo(false, ""));
    }

    @Getter
    public static class ScheduleForm {
        @NotNull(message = "教学任务不能为空")
        private Long teachingTaskId;
        @NotNull(message = "时间段不能为空")
        private Long timeSlotId;
        @NotNull(message = "教室不能为空")
        private Long classroomId;
    }
}
