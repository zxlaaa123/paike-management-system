package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.enums.V5ConsistencyStatus;
import com.paike.scheduler.common.enums.V5RegressionStatus;
import com.paike.scheduler.common.enums.V5RepairTaskStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ScheduleConsistencyCheck;
import com.paike.scheduler.entity.ScheduleRegressionTest;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.mapper.ScheduleConsistencyCheckMapper;
import com.paike.scheduler.mapper.ScheduleRegressionTestMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.service.dto.V5ConsistencyCheckRecordRequest;
import com.paike.scheduler.service.dto.V5RegressionTestRecordRequest;
import com.paike.scheduler.service.dto.V5RepairTaskCreateRequest;
import com.paike.scheduler.service.vo.V5ConsistencyCheckVo;
import com.paike.scheduler.service.vo.V5RegressionTestVo;
import com.paike.scheduler.service.vo.V5RepairTaskVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class V5Stage1DataService {

    private final ScheduleRepairTaskMapper repairTaskMapper;
    private final ScheduleConsistencyCheckMapper consistencyCheckMapper;
    private final ScheduleRegressionTestMapper regressionTestMapper;

    @Transactional(rollbackFor = Exception.class)
    public V5RepairTaskVo createRepairTask(V5RepairTaskCreateRequest request) {
        ScheduleRepairTask task = new ScheduleRepairTask();
        task.setSemesterId(request.getSemesterId());
        task.setPlanId(request.getPlanId());
        task.setSourcePlanId(request.getSourcePlanId());
        task.setSourceScheduleId(request.getSourceScheduleId());
        task.setTaskCode(request.getTaskCode().trim());
        task.setTaskType(request.getTaskType().trim().toUpperCase(Locale.ROOT));
        task.setStatus(V5RepairTaskStatus.PENDING.getCode());
        task.setTriggerSource(normalizeOrDefault(request.getTriggerSource(), "MANUAL"));
        task.setRiskTypes(trimToNull(request.getRiskTypes()));
        task.setTargetItemCount(0);
        task.setLockedItemCount(0);
        task.setProcessedItemCount(0);
        task.setSuccessItemCount(0);
        task.setFailureItemCount(0);
        repairTaskMapper.insert(task);
        return toVo(task);
    }

    public List<V5RepairTaskVo> listRepairTasksByPlan(Long planId) {
        return repairTaskMapper.selectList(new LambdaQueryWrapper<ScheduleRepairTask>()
                        .eq(ScheduleRepairTask::getPlanId, planId)
                        .orderByDesc(ScheduleRepairTask::getCreatedAt)
                        .orderByDesc(ScheduleRepairTask::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public V5ConsistencyCheckVo recordConsistencyCheck(V5ConsistencyCheckRecordRequest request) {
        String status = normalizeUpper(request.getStatus());
        ensureConsistencyStatus(status);

        ScheduleConsistencyCheck entity = new ScheduleConsistencyCheck();
        entity.setSemesterId(request.getSemesterId());
        entity.setPlanId(request.getPlanId());
        entity.setSourcePlanId(request.getSourcePlanId());
        entity.setScheduleId(request.getScheduleId());
        entity.setCheckType(request.getCheckType().trim().toUpperCase(Locale.ROOT));
        entity.setCheckScope(normalizeOrDefault(request.getCheckScope(), "SEMESTER"));
        entity.setStatus(status);
        entity.setIssueCount(request.getIssueCount() == null ? 0 : Math.max(0, request.getIssueCount()));
        entity.setBlockingIssueCount(request.getBlockingIssueCount() == null ? 0 : Math.max(0, request.getBlockingIssueCount()));
        entity.setResultSummary(trimToNull(request.getResultSummary()));
        entity.setDetailJson(trimToNull(request.getDetailJson()));
        entity.setCheckedAt(LocalDateTime.now());
        consistencyCheckMapper.insert(entity);
        return toVo(entity);
    }

    public List<V5ConsistencyCheckVo> listConsistencyChecksBySemester(Long semesterId) {
        return consistencyCheckMapper.selectList(new LambdaQueryWrapper<ScheduleConsistencyCheck>()
                        .eq(ScheduleConsistencyCheck::getSemesterId, semesterId)
                        .orderByDesc(ScheduleConsistencyCheck::getCreatedAt)
                        .orderByDesc(ScheduleConsistencyCheck::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public V5RegressionTestVo recordRegressionTest(V5RegressionTestRecordRequest request) {
        String status = normalizeUpper(request.getStatus());
        ensureRegressionStatus(status);

        ScheduleRegressionTest entity = new ScheduleRegressionTest();
        entity.setSemesterId(request.getSemesterId());
        entity.setPlanId(request.getPlanId());
        entity.setSourcePlanId(request.getSourcePlanId());
        entity.setTestSuite(request.getTestSuite().trim());
        entity.setTestCase(trimToNull(request.getTestCase()));
        entity.setTestStage(trimToNull(request.getTestStage()));
        entity.setStatus(status);
        entity.setDurationMs(request.getDurationMs());
        entity.setExecutedBy(trimToNull(request.getExecutedBy()));
        entity.setBuildVersion(trimToNull(request.getBuildVersion()));
        entity.setErrorMessage(trimToNull(request.getErrorMessage()));
        entity.setExtraJson(trimToNull(request.getExtraJson()));
        entity.setExecutedAt(LocalDateTime.now());
        regressionTestMapper.insert(entity);
        return toVo(entity);
    }

    public List<V5RegressionTestVo> listRegressionTests(String testStage) {
        LambdaQueryWrapper<ScheduleRegressionTest> wrapper = new LambdaQueryWrapper<ScheduleRegressionTest>()
                .orderByDesc(ScheduleRegressionTest::getCreatedAt)
                .orderByDesc(ScheduleRegressionTest::getId);
        if (trimToNull(testStage) != null) {
            wrapper.eq(ScheduleRegressionTest::getTestStage, testStage.trim());
        }
        return regressionTestMapper.selectList(wrapper).stream().map(this::toVo).toList();
    }

    private void ensureConsistencyStatus(String status) {
        for (V5ConsistencyStatus candidate : V5ConsistencyStatus.values()) {
            if (candidate.getCode().equals(status)) return;
        }
        throw new BusinessException("不支持的一致性检查状态：" + status);
    }

    private void ensureRegressionStatus(String status) {
        for (V5RegressionStatus candidate : V5RegressionStatus.values()) {
            if (candidate.getCode().equals(status)) return;
        }
        throw new BusinessException("不支持的回归测试状态：" + status);
    }

    private V5RepairTaskVo toVo(ScheduleRepairTask task) {
        V5RepairTaskVo vo = new V5RepairTaskVo();
        vo.setId(task.getId());
        vo.setSemesterId(task.getSemesterId());
        vo.setPlanId(task.getPlanId());
        vo.setTaskCode(task.getTaskCode());
        vo.setTaskType(task.getTaskType());
        vo.setStatus(task.getStatus());
        vo.setResultPlanId(task.getResultPlanId());
        vo.setStartedAt(task.getStartedAt());
        vo.setFinishedAt(task.getFinishedAt());
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    private V5ConsistencyCheckVo toVo(ScheduleConsistencyCheck entity) {
        V5ConsistencyCheckVo vo = new V5ConsistencyCheckVo();
        vo.setId(entity.getId());
        vo.setSemesterId(entity.getSemesterId());
        vo.setPlanId(entity.getPlanId());
        vo.setCheckType(entity.getCheckType());
        vo.setStatus(entity.getStatus());
        vo.setIssueCount(entity.getIssueCount());
        vo.setBlockingIssueCount(entity.getBlockingIssueCount());
        vo.setResultSummary(entity.getResultSummary());
        vo.setCheckedAt(entity.getCheckedAt());
        return vo;
    }

    private V5RegressionTestVo toVo(ScheduleRegressionTest entity) {
        V5RegressionTestVo vo = new V5RegressionTestVo();
        vo.setId(entity.getId());
        vo.setTestSuite(entity.getTestSuite());
        vo.setTestCase(entity.getTestCase());
        vo.setTestStage(entity.getTestStage());
        vo.setStatus(entity.getStatus());
        vo.setDurationMs(entity.getDurationMs());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setExecutedAt(entity.getExecutedAt());
        return vo;
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException("状态不能为空");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

