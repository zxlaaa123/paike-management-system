package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.entity.ScheduleConsistencyCheck;
import com.paike.scheduler.mapper.ScheduleConsistencyCheckMapper;
import com.paike.scheduler.service.vo.V5ConsistencyCheckReportVo;
import com.paike.scheduler.service.vo.V6ConsistencyCheckDetailVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class V6ConsistencyCheckService {

    private final ScheduleConsistencyCheckMapper consistencyCheckMapper;
    private final V5ConsistencyCheckService v5ConsistencyCheckService;
    private final ObjectMapper objectMapper;

    public Page<ScheduleConsistencyCheck> list(
            String status,
            String checkType,
            Long semesterId,
            Long planId,
            int page,
            int size
    ) {
        LambdaQueryWrapper<ScheduleConsistencyCheck> wrapper = new LambdaQueryWrapper<>();
        if (hasText(status)) {
            wrapper.eq(ScheduleConsistencyCheck::getStatus, status.trim().toUpperCase());
        }
        if (hasText(checkType)) {
            wrapper.eq(ScheduleConsistencyCheck::getCheckType, checkType.trim());
        }
        if (semesterId != null) {
            wrapper.eq(ScheduleConsistencyCheck::getSemesterId, semesterId);
        }
        if (planId != null) {
            wrapper.eq(ScheduleConsistencyCheck::getPlanId, planId);
        }
        wrapper.orderByDesc(ScheduleConsistencyCheck::getCheckedAt)
                .orderByDesc(ScheduleConsistencyCheck::getId);
        return consistencyCheckMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public V6ConsistencyCheckDetailVo getById(Long id) {
        ScheduleConsistencyCheck record = consistencyCheckMapper.selectById(id);
        V6ConsistencyCheckDetailVo detail = new V6ConsistencyCheckDetailVo();
        detail.setRecord(record);
        V5ConsistencyCheckReportVo report = record == null ? null : readReport(record.getDetailJson());
        detail.setReport(report);
        detail.setIssues(report == null || report.getIssues() == null ? List.of() : report.getIssues());
        return detail;
    }

    public V5ConsistencyCheckReportVo run(Long taskId, Long planId) {
        return v5ConsistencyCheckService.check(taskId, planId, true);
    }

    private V5ConsistencyCheckReportVo readReport(String detailJson) {
        if (!hasText(detailJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(detailJson, V5ConsistencyCheckReportVo.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

