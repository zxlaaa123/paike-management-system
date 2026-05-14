package com.paike.scheduler.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.service.SemesterService;
import com.paike.scheduler.service.dto.SemesterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v3/semesters")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;

    @GetMapping
    public Result<Page<Semester>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return Result.success(semesterService.list(keyword, status, page, size));
    }

    @GetMapping("/all")
    public Result<List<Semester>> listAll() {
        return Result.success(semesterService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Semester> getById(@PathVariable Long id) {
        return Result.success(semesterService.getById(id));
    }

    @GetMapping("/current")
    public Result<Semester> getCurrent() {
        return Result.success(semesterService.getCurrentSemester());
    }

    @PostMapping
    public Result<Semester> create(@Valid @RequestBody SemesterRequest request) {
        return Result.success(semesterService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Semester> update(@PathVariable Long id, @Valid @RequestBody SemesterRequest request) {
        return Result.success(semesterService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        semesterService.delete(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/current")
    public Result<Void> setCurrent(@PathVariable Long id) {
        semesterService.setCurrent(id);
        return Result.success("已设置为当前学期", null);
    }
}
