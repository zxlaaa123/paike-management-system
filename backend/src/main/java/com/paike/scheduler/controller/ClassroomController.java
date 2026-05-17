package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.service.ClassroomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    @GetMapping
    public Result<Page<Classroom>> list(
        @RequestParam(required = false) String roomName,
        @RequestParam(required = false) String building,
        @RequestParam(required = false) String roomType,
        @RequestParam(required = false) Integer status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(classroomService.list(roomName, building, roomType, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Classroom> getById(@PathVariable Long id) {
        return Result.success(classroomService.getById(id));
    }

    @PostMapping
    public Result<Classroom> create(@Valid @RequestBody ClassroomForm form) {
        Classroom classroom = new Classroom();
        classroom.setRoomName(form.getRoomName());
        classroom.setBuilding(form.getBuilding());
        classroom.setCapacity(form.getCapacity());
        classroom.setRoomType(form.getRoomType());
        classroom.setStatus(form.getStatus() != null ? form.getStatus() : 1);
        classroom.setRemark(form.getRemark());
        return Result.success(classroomService.create(classroom));
    }

    @PutMapping("/{id}")
    public Result<Classroom> update(@PathVariable Long id, @Valid @RequestBody ClassroomForm form) {
        Classroom classroom = new Classroom();
        classroom.setRoomName(form.getRoomName());
        classroom.setBuilding(form.getBuilding());
        classroom.setCapacity(form.getCapacity());
        classroom.setRoomType(form.getRoomType());
        classroom.setStatus(form.getStatus());
        classroom.setRemark(form.getRemark());
        return Result.success(classroomService.update(id, classroom));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        classroomService.delete(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        classroomService.updateStatus(id, form.getStatus());
        return Result.success("操作成功", null);
    }

    @GetMapping("/all")
    public Result<List<Classroom>> listAll() {
        return Result.success(classroomService.listAll());
    }

    @Getter
    public static class ClassroomForm {
        @NotBlank(message = "教室名称不能为空")
        private String roomName;
        private String building;
        @Min(value = 1, message = "教室容量必须大于0")
        private Integer capacity;
        private String roomType;
        private Integer status;
        private String remark;
    }

    @Data
    public static class StatusForm {
        @NotNull(message = "状态不能为空")
        private Integer status;
    }
}
