package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.TeacherUnavailableTime;
import com.paike.scheduler.service.TeacherUnavailableTimeService;
import com.paike.scheduler.service.dto.TeacherUnavailableTimeForm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/teacher-unavailable-times")
@RequiredArgsConstructor
public class TeacherUnavailableTimeController {

    private final TeacherUnavailableTimeService unavailableTimeService;

    @GetMapping
    public Result<Page<TeacherUnavailableTime>> list(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) Long timeSlotId,
            @RequestParam(required = false) Integer status,
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            @RequestParam(defaultValue = "1") int page,
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 200, message = "每页数量不能超过200")
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<TeacherUnavailableTime> result = unavailableTimeService.list(teacherId, teacherName, timeSlotId, status, page, size);
        return Result.success(result);
    }

    @PostMapping
    public Result<TeacherUnavailableTime> create(@Valid @RequestBody TeacherUnavailableTimeForm form) {
        TeacherUnavailableTime result = unavailableTimeService.create(form);
        return Result.success(result);
    }

    @PutMapping("/{id}")
    public Result<TeacherUnavailableTime> update(@PathVariable Long id, @Valid @RequestBody TeacherUnavailableTimeForm form) {
        TeacherUnavailableTime result = unavailableTimeService.update(id, form);
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        unavailableTimeService.delete(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        unavailableTimeService.updateStatus(id, form.getStatus());
        return Result.success("操作成功", null);
    }

    @Data
    public static class StatusForm {
        @NotNull(message = "状态不能为空")
        private Integer status;
    }
}
