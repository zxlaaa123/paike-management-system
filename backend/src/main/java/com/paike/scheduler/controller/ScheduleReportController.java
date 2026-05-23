package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.entity.ScheduleReport;
import com.paike.scheduler.service.V4ScheduleReportService;
import com.paike.scheduler.service.dto.V4ScheduleReportGenerateRequest;
import com.paike.scheduler.service.vo.ScheduleReportItemVo;
import com.paike.scheduler.service.vo.ScheduleReportListVo;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v4/schedule-reports")
@RequiredArgsConstructor
public class ScheduleReportController {

    private final V4ScheduleReportService scheduleReportService;

    @PostMapping("/plans/{planId}/generate")
    public Result<ScheduleReportItemVo> generatePlanReport(
            @PathVariable Long planId,
            @Valid @RequestBody(required = false) V4ScheduleReportGenerateRequest request
    ) {
        ScheduleReportItemVo result = scheduleReportService.generateReport(planId, request);
        return Result.success("报告生成成功", result);
    }

    @GetMapping("/plans/{planId}")
    public Result<ScheduleReportListVo> listPlanReports(@PathVariable Long planId) {
        return Result.success(scheduleReportService.listPlanReports(planId));
    }

    @GetMapping("/{reportId}/download")
    public void downloadReport(@PathVariable Long reportId, HttpServletResponse response) throws IOException {
        ScheduleReport report = scheduleReportService.findReport(reportId);
        Path file = scheduleReportService.resolveDownloadFile(reportId);
        String fileName = file.getFileName().toString();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("HTML".equalsIgnoreCase(report.getFormat()) ? "text/html; charset=UTF-8" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        try (InputStream inputStream = Files.newInputStream(file)) {
            StreamUtils.copy(inputStream, response.getOutputStream());
            response.flushBuffer();
        }
    }
}
