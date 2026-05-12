package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.mapper.ClassroomMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomMapper classroomMapper;

    @GetMapping
    public Result<Page<Classroom>> list(
        @RequestParam(required = false) String roomName,
        @RequestParam(required = false) String building,
        @RequestParam(required = false) String roomType,
        @RequestParam(required = false) Integer status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        LambdaQueryWrapper<Classroom> wrapper = new LambdaQueryWrapper<Classroom>()
            .eq(Classroom::getDeleted, 0);
        if (roomName != null && !roomName.isBlank()) {
            wrapper.like(Classroom::getRoomName, roomName);
        }
        if (building != null && !building.isBlank()) {
            wrapper.like(Classroom::getBuilding, building);
        }
        if (roomType != null && !roomType.isBlank()) {
            wrapper.eq(Classroom::getRoomType, roomType);
        }
        if (status != null) {
            wrapper.eq(Classroom::getStatus, status);
        }
        wrapper.orderByDesc(Classroom::getCreateTime);
        Page<Classroom> result = classroomMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Classroom> getById(@PathVariable Long id) {
        Classroom classroom = classroomMapper.selectById(id);
        if (classroom == null || classroom.getDeleted() == 1) {
            return Result.fail(404, "教室不存在");
        }
        return Result.success(classroom);
    }

    @PostMapping
    public Result<Classroom> create(@Valid @RequestBody ClassroomForm form) {
        long count = classroomMapper.selectCount(new LambdaQueryWrapper<Classroom>()
            .eq(Classroom::getRoomName, form.getRoomName())
            .eq(Classroom::getDeleted, 0));
        if (count > 0) {
            return Result.fail(400, "教室名称已存在");
        }
        Classroom classroom = new Classroom();
        classroom.setRoomName(form.getRoomName());
        classroom.setBuilding(form.getBuilding());
        classroom.setCapacity(form.getCapacity());
        classroom.setRoomType(form.getRoomType());
        classroom.setStatus(form.getStatus() != null ? form.getStatus() : 1);
        classroom.setRemark(form.getRemark());
        classroom.setDeleted(0);
        classroom.setCreateTime(LocalDateTime.now());
        classroom.setUpdateTime(LocalDateTime.now());
        classroomMapper.insert(classroom);
        return Result.success(classroom);
    }

    @PutMapping("/{id}")
    public Result<Classroom> update(@PathVariable Long id, @Valid @RequestBody ClassroomForm form) {
        Classroom classroom = classroomMapper.selectById(id);
        if (classroom == null || classroom.getDeleted() == 1) {
            return Result.fail(404, "教室不存在");
        }
        long count = classroomMapper.selectCount(new LambdaQueryWrapper<Classroom>()
            .eq(Classroom::getRoomName, form.getRoomName())
            .eq(Classroom::getDeleted, 0)
            .ne(Classroom::getId, id));
        if (count > 0) {
            return Result.fail(400, "教室名称已存在");
        }
        classroom.setRoomName(form.getRoomName());
        classroom.setBuilding(form.getBuilding());
        classroom.setCapacity(form.getCapacity());
        classroom.setRoomType(form.getRoomType());
        classroom.setStatus(form.getStatus());
        classroom.setRemark(form.getRemark());
        classroom.setUpdateTime(LocalDateTime.now());
        classroomMapper.updateById(classroom);
        return Result.success(classroom);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Classroom classroom = classroomMapper.selectById(id);
        if (classroom == null || classroom.getDeleted() == 1) {
            return Result.fail(404, "教室不存在");
        }
        classroom.setDeleted(1);
        classroom.setUpdateTime(LocalDateTime.now());
        classroomMapper.updateById(classroom);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody StatusForm form) {
        Classroom classroom = classroomMapper.selectById(id);
        if (classroom == null || classroom.getDeleted() == 1) {
            return Result.fail(404, "教室不存在");
        }
        classroom.setStatus(form.getStatus());
        classroom.setUpdateTime(LocalDateTime.now());
        classroomMapper.updateById(classroom);
        return Result.success("操作成功", null);
    }

    @GetMapping("/all")
    public Result<List<Classroom>> listAll() {
        List<Classroom> list = classroomMapper.selectList(new LambdaQueryWrapper<Classroom>()
            .eq(Classroom::getDeleted, 0)
            .eq(Classroom::getStatus, 1)
            .orderByAsc(Classroom::getRoomName));
        return Result.success(list);
    }

    @Data
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
        private Integer status;
    }
}
