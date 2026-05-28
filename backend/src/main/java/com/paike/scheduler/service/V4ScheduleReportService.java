package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.ScheduleReport;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleReportMapper;
import com.paike.scheduler.service.dto.V4ScheduleReportGenerateRequest;
import com.paike.scheduler.service.vo.ScheduleAnalysisSummaryVo;
import com.paike.scheduler.service.vo.ScheduleReportItemVo;
import com.paike.scheduler.service.vo.ScheduleReportListVo;
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class V4ScheduleReportService {

    private static final String STATUS_GENERATED = "GENERATED";
    private static final List<String> REPORT_TYPES = List.of("ANALYSIS", "COMPARE", "RISK", "TEACHER_LOAD", "ROOM_USAGE");
    private static final List<String> FORMATS = List.of("HTML", "EXCEL");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SchedulePlanMapper schedulePlanMapper;
    private final ScheduleReportMapper scheduleReportMapper;
    private final V4ScheduleAnalysisService scheduleAnalysisService;
    private final V4ScheduleRiskService scheduleRiskService;

    @Value("${paike.report.dir:data/reports}")
    private String reportDir = "data/reports";

    public ScheduleReportItemVo generateReport(Long planId, V4ScheduleReportGenerateRequest request) {
        SchedulePlan plan = schedulePlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }

        String reportType = normalizeReportType(request == null ? null : request.getReportType());
        String format = normalizeFormat(request == null ? null : request.getFormat());
        boolean includeCharts = request == null || request.getIncludeCharts() == null || request.getIncludeCharts();
        boolean includeRisks = request == null || request.getIncludeRisks() == null || request.getIncludeRisks();
        boolean includeSuggestions = request == null || request.getIncludeSuggestions() == null || request.getIncludeSuggestions();

        ScheduleAnalysisSummaryVo summary = scheduleAnalysisService.getPlanSummary(planId);
        ScheduleRiskListVo riskList = includeRisks ? scheduleRiskService.getPlanRisks(planId, null, null, false) : null;
        Path filePath = "EXCEL".equals(format)
                ? buildExcelReport(plan, summary, reportType, includeCharts, includeRisks, includeSuggestions, riskList)
                : buildHtmlReport(plan, summary, reportType, includeCharts, includeRisks, includeSuggestions, riskList);

        ScheduleReport report = new ScheduleReport();
        report.setPlanId(planId);
        report.setSemesterId(plan.getSemesterId());
        report.setReportType(reportType);
        report.setFormat(format);
        report.setStatus(STATUS_GENERATED);
        report.setIncludeCharts(includeCharts ? 1 : 0);
        report.setIncludeRisks(includeRisks ? 1 : 0);
        report.setIncludeSuggestions(includeSuggestions ? 1 : 0);
        report.setFilePath(filePath.toString());
        scheduleReportMapper.insert(report);
        return toItemVo(report);
    }

    public ScheduleReportListVo listPlanReports(Long planId) {
        SchedulePlan plan = schedulePlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        List<ScheduleReport> records = scheduleReportMapper.selectList(
                new LambdaQueryWrapper<ScheduleReport>()
                        .eq(ScheduleReport::getPlanId, planId)
                        .orderByDesc(ScheduleReport::getCreatedAt)
                        .orderByDesc(ScheduleReport::getId));
        ScheduleReportListVo result = new ScheduleReportListVo();
        result.setPlanId(planId);
        result.setSemesterId(plan.getSemesterId());
        result.setItems(records.stream().map(this::toItemVo).toList());
        return result;
    }

    public Path resolveDownloadFile(Long reportId) {
        ScheduleReport report = scheduleReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        if (report.getFilePath() == null || report.getFilePath().isBlank()) {
            throw new BusinessException(404, "报告文件不存在，请重新生成");
        }
        Path reportDir = ensureReportDir();
        Path rawPath = Path.of(report.getFilePath());
        Path filePath = rawPath.isAbsolute()
                ? rawPath.normalize()
                : rawPath.toAbsolutePath().normalize();
        if (!filePath.startsWith(reportDir)) {
            throw new BusinessException(403, "报告文件路径非法");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException(404, "报告文件不存在，请重新生成");
        }
        return filePath;
    }

    public ScheduleReport findReport(Long reportId) {
        ScheduleReport report = scheduleReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        return report;
    }

    private ScheduleReportItemVo toItemVo(ScheduleReport report) {
        ScheduleReportItemVo item = new ScheduleReportItemVo();
        item.setReportId(report.getId());
        item.setPlanId(report.getPlanId());
        item.setSemesterId(report.getSemesterId());
        item.setReportType(report.getReportType());
        item.setFormat(report.getFormat());
        item.setStatus(report.getStatus());
        item.setDownloadUrl("/api/v4/schedule-reports/" + report.getId() + "/download");
        item.setCreatedAt(report.getCreatedAt());
        return item;
    }

    private String normalizeReportType(String raw) {
        String value = (raw == null || raw.isBlank()) ? "ANALYSIS" : raw.trim().toUpperCase(Locale.ROOT);
        if (!REPORT_TYPES.contains(value)) {
            throw new BusinessException("不支持的报告类型");
        }
        return value;
    }

    private String normalizeFormat(String raw) {
        String value = (raw == null || raw.isBlank()) ? "HTML" : raw.trim().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(value)) {
            throw new BusinessException("当前阶段仅支持 HTML 或 EXCEL 报告");
        }
        return value;
    }

    private Path ensureReportDir() {
        try {
            Path dir = Path.of(reportDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new BusinessException("创建报告目录失败: " + e.getMessage());
        }
    }

    private Path buildHtmlReport(
            SchedulePlan plan,
            ScheduleAnalysisSummaryVo summary,
            String reportType,
            boolean includeCharts,
            boolean includeRisks,
            boolean includeSuggestions,
            ScheduleRiskListVo riskList
    ) {
        Path dir = ensureReportDir();
        Path file = dir.resolve(buildFileName(plan.getId(), reportType, "html"));
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"UTF-8\"><title>排课分析报告</title>")
                .append("<style>body{font-family:Arial,sans-serif;padding:24px;color:#1f2937}h1,h2{color:#0f172a}table{border-collapse:collapse;width:100%;margin-top:8px}th,td{border:1px solid #dbe2ea;padding:8px;text-align:left}</style>")
                .append("</head><body>");
        html.append("<h1>V4 排课分析报告</h1>");
        html.append("<p>方案：").append(safeHtml(plan.getName())).append("（ID ").append(plan.getId()).append("）</p>");
        html.append("<p>报告类型：").append(reportType).append("，生成时间：").append(DISPLAY_TIME.format(LocalDateTime.now())).append("</p>");
        html.append("<h2>质量总览</h2><table>")
                .append("<tr><th>总分</th><th>已排任务</th><th>未排任务</th><th>冲突数量</th><th>质量等级</th></tr>")
                .append("<tr><td>").append(summary.getTotalScore()).append("</td><td>").append(summary.getScheduledCount())
                .append("</td><td>").append(summary.getUnscheduledCount()).append("</td><td>").append(summary.getConflictCount())
                .append("</td><td>").append(safeHtml(summary.getQualityLevel())).append("</td></tr></table>");

        if (includeRisks && riskList != null) {
            html.append("<h2>风险统计</h2><table>")
                    .append("<tr><th>高风险</th><th>中风险</th><th>低风险</th><th>未解决</th></tr>")
                    .append("<tr><td>").append(riskList.getHighRiskCount()).append("</td><td>").append(riskList.getMediumRiskCount())
                    .append("</td><td>").append(riskList.getLowRiskCount()).append("</td><td>").append(riskList.getUnresolvedCount())
                    .append("</td></tr></table>");
        }

        if (includeSuggestions && summary.getSuggestions() != null && !summary.getSuggestions().isEmpty()) {
            html.append("<h2>优化建议</h2><ul>");
            for (String suggestion : summary.getSuggestions()) {
                html.append("<li>").append(safeHtml(suggestion)).append("</li>");
            }
            html.append("</ul>");
        }

        if (includeCharts) {
            html.append("<p>图表说明：当前阶段由前端图表页提供交互展示，报告内保留统计摘要。</p>");
        }

        html.append("</body></html>");
        try {
            Files.writeString(file, html.toString(), StandardCharsets.UTF_8);
            return file.toAbsolutePath();
        } catch (IOException e) {
            throw new BusinessException("写入 HTML 报告失败: " + e.getMessage());
        }
    }

    private Path buildExcelReport(
            SchedulePlan plan,
            ScheduleAnalysisSummaryVo summary,
            String reportType,
            boolean includeCharts,
            boolean includeRisks,
            boolean includeSuggestions,
            ScheduleRiskListVo riskList
    ) {
        Path dir = ensureReportDir();
        Path file = dir.resolve(buildFileName(plan.getId(), reportType, "xlsx"));
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("分析报告");
            int row = 0;
            sheet.createRow(row++).createCell(0).setCellValue("V4 排课分析报告");
            sheet.createRow(row++).createCell(0).setCellValue("方案：" + safeText(plan.getName()) + "（ID " + plan.getId() + "）");
            sheet.createRow(row++).createCell(0).setCellValue("报告类型：" + reportType);
            sheet.createRow(row++).createCell(0).setCellValue("生成时间：" + DISPLAY_TIME.format(LocalDateTime.now()));
            row++;

            sheet.createRow(row++).createCell(0).setCellValue("质量总览");
            sheet.createRow(row).createCell(0).setCellValue("总分");
            sheet.getRow(row).createCell(1).setCellValue(summary.getTotalScore() == null ? 0D : summary.getTotalScore().doubleValue());
            sheet.getRow(row).createCell(2).setCellValue("已排任务");
            sheet.getRow(row++).createCell(3).setCellValue(summary.getScheduledCount());
            sheet.createRow(row).createCell(0).setCellValue("未排任务");
            sheet.getRow(row).createCell(1).setCellValue(summary.getUnscheduledCount());
            sheet.getRow(row).createCell(2).setCellValue("冲突数量");
            sheet.getRow(row++).createCell(3).setCellValue(summary.getConflictCount());
            sheet.createRow(row).createCell(0).setCellValue("质量等级");
            sheet.getRow(row++).createCell(1).setCellValue(safeText(summary.getQualityLevel()));
            row++;

            if (includeRisks && riskList != null) {
                sheet.createRow(row++).createCell(0).setCellValue("风险统计");
                sheet.createRow(row).createCell(0).setCellValue("高风险");
                sheet.getRow(row).createCell(1).setCellValue(riskList.getHighRiskCount());
                sheet.getRow(row).createCell(2).setCellValue("中风险");
                sheet.getRow(row).createCell(3).setCellValue(riskList.getMediumRiskCount());
                row++;
                sheet.createRow(row).createCell(0).setCellValue("低风险");
                sheet.getRow(row).createCell(1).setCellValue(riskList.getLowRiskCount());
                sheet.getRow(row).createCell(2).setCellValue("未解决");
                sheet.getRow(row++).createCell(3).setCellValue(riskList.getUnresolvedCount());
                row++;
            }

            if (includeSuggestions && summary.getSuggestions() != null && !summary.getSuggestions().isEmpty()) {
                sheet.createRow(row++).createCell(0).setCellValue("优化建议");
                for (String suggestion : summary.getSuggestions()) {
                    sheet.createRow(row++).createCell(0).setCellValue("- " + suggestion);
                }
                row++;
            }

            sheet.createRow(row).createCell(0).setCellValue(includeCharts
                    ? "图表说明：当前阶段由前端图表页提供交互展示，报告内保留统计摘要。"
                    : "图表说明：本次生成未包含图表。");
            sheet.setColumnWidth(0, 24 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            sheet.setColumnWidth(2, 18 * 256);
            sheet.setColumnWidth(3, 18 * 256);

            Files.createDirectories(file.getParent());
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
            return file.toAbsolutePath();
        } catch (IOException e) {
            throw new BusinessException("写入 EXCEL 报告失败: " + e.getMessage());
        }
    }

    private String buildFileName(Long planId, String reportType, String ext) {
        return "schedule-report-plan-" + planId + "-" + reportType.toLowerCase(Locale.ROOT) + "-" + FILE_TIME.format(LocalDateTime.now()) + "." + ext;
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String safeHtml(String value) {
        String text = safeText(value);
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }
}
