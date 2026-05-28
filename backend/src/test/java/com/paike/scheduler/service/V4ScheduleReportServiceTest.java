package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.ScheduleReport;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleReportMapper;
import com.paike.scheduler.service.dto.V4ScheduleReportGenerateRequest;
import com.paike.scheduler.service.vo.ScheduleAnalysisSummaryVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V4ScheduleReportServiceTest {

    private SchedulePlanMapper schedulePlanMapper;
    private ScheduleReportMapper scheduleReportMapper;
    private V4ScheduleAnalysisService scheduleAnalysisService;
    private V4ScheduleReportService service;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        schedulePlanMapper = mock(SchedulePlanMapper.class);
        scheduleReportMapper = mock(ScheduleReportMapper.class);
        scheduleAnalysisService = mock(V4ScheduleAnalysisService.class);
        service = new V4ScheduleReportService(
                schedulePlanMapper,
                scheduleReportMapper,
                scheduleAnalysisService,
                mock(V4ScheduleRiskService.class));
        ReflectionTestUtils.setField(service, "reportDir", tempDir.resolve("reports").toString());
    }

    @Test
    void generateReport_escapesHtmlFieldsInHtmlOutput() throws Exception {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(9L);
        plan.setName("<script>alert('x')</script>");
        when(schedulePlanMapper.selectById(9L)).thenReturn(plan);
        when(scheduleAnalysisService.getPlanSummary(9L)).thenReturn(summaryWithUnsafeText());

        V4ScheduleReportGenerateRequest request = new V4ScheduleReportGenerateRequest();
        request.setFormat("HTML");
        request.setIncludeRisks(false);
        request.setIncludeCharts(false);
        service.generateReport(9L, request);

        ArgumentCaptor<ScheduleReport> captor = ArgumentCaptor.forClass(ScheduleReport.class);
        org.mockito.Mockito.verify(scheduleReportMapper).insert(captor.capture());
        String html = Files.readString(Path.of(captor.getValue().getFilePath()), StandardCharsets.UTF_8);

        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img"));
        assertTrue(html.contains("&lt;script&gt;alert(&#x27;x&#x27;)&lt;&#x2F;script&gt;"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
    }

    @Test
    void resolveDownloadFile_rejectsPathOutsideReportDirectory() {
        ScheduleReport report = new ScheduleReport();
        report.setId(1L);
        report.setFilePath(tempDir.resolve("secret.txt").toString());
        when(scheduleReportMapper.selectById(1L)).thenReturn(report);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resolveDownloadFile(1L));

        assertEquals(403, ex.getCode());
    }

    @Test
    void resolveDownloadFile_allowsFileInsideReportDirectory() throws Exception {
        Path reportDir = tempDir.resolve("reports").toAbsolutePath().normalize();
        Files.createDirectories(reportDir);
        Path file = reportDir.resolve("download-test.txt");
        Files.writeString(file, "ok", StandardCharsets.UTF_8);
        ScheduleReport report = new ScheduleReport();
        report.setId(2L);
        report.setFilePath(file.toString());
        when(scheduleReportMapper.selectById(2L)).thenReturn(report);

        assertEquals(file, service.resolveDownloadFile(2L));
    }

    private ScheduleAnalysisSummaryVo summaryWithUnsafeText() {
        ScheduleAnalysisSummaryVo summary = new ScheduleAnalysisSummaryVo();
        summary.setTotalScore(BigDecimal.TEN);
        summary.setScheduledCount(1);
        summary.setUnscheduledCount(0);
        summary.setConflictCount(0);
        summary.setQualityLevel("<img src=x onerror=alert(1)>");
        summary.setSuggestions(List.of("<script>alert(2)</script>"));
        return summary;
    }
}
