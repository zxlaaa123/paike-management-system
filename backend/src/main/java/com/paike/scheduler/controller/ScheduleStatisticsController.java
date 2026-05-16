package com.paike.scheduler.controller;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.ScheduleStatisticsService;
import com.paike.scheduler.service.SemesterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/statistics")
@RequiredArgsConstructor
public class ScheduleStatisticsController {

    private final ScheduleStatisticsService statisticsService;
    private final SemesterService semesterService;

    /**
     * 教师工作量统计
     * GET /api/v3/statistics/teacher-workload?semesterId=2&planId=34
     * 不传 planId 则统计正式课表
     */
    @GetMapping("/teacher-workload")
    public Result<List<Map<String, Object>>> teacherWorkload(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long planId
    ) {
        Long resolvedSemesterId = resolveSemesterId(semesterId);
        return Result.success(statisticsService.teacherWorkload(resolvedSemesterId, planId));
    }

    /**
     * 教室利用率统计
     * GET /api/v3/statistics/classroom-utilization?semesterId=2&planId=34
     * 不传 planId 则统计正式课表
     */
    @GetMapping("/classroom-utilization")
    public Result<List<Map<String, Object>>> classroomUtilization(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long planId
    ) {
        Long resolvedSemesterId = resolveSemesterId(semesterId);
        return Result.success(statisticsService.classroomUtilization(resolvedSemesterId, planId));
    }

    /**
     * 班级课表均衡度统计
     * GET /api/v3/statistics/class-balance?semesterId=2&planId=34
     * 不传 planId 则统计正式课表
     */
    @GetMapping("/class-balance")
    public Result<List<Map<String, Object>>> classBalance(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long planId
    ) {
        Long resolvedSemesterId = resolveSemesterId(semesterId);
        return Result.success(statisticsService.classBalance(resolvedSemesterId, planId));
    }

    /**
     * 方案统计总览
     * GET /api/v3/statistics/plan-overview?semesterId=2
     */
    @GetMapping("/plan-overview")
    public Result<Map<String, Object>> planOverview(
            @RequestParam(required = false) Long semesterId
    ) {
        Long resolvedSemesterId = resolveSemesterId(semesterId);
        return Result.success(statisticsService.planOverview(resolvedSemesterId));
    }

    /**
     * 首页统计
     * GET /api/v3/statistics/dashboard?semesterId=2
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(
            @RequestParam(required = false) Long semesterId
    ) {
        Long resolvedSemesterId = resolveSemesterId(semesterId);
        return Result.success(statisticsService.dashboardStats(resolvedSemesterId));
    }

    private Long resolveSemesterId(Long semesterId) {
        if (semesterId != null) {
            return semesterId;
        }
        return semesterService.getCurrentSemester().getId();
    }
}
