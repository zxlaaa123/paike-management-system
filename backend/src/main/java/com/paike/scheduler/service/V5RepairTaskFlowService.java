package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.V5RepairTaskStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.service.dto.V5RepairTaskFlowCreateRequest;
import com.paike.scheduler.service.dto.V5RepairTaskStatusUpdateRequest;
import com.paike.scheduler.service.vo.ScheduleRiskIssueVo;
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import com.paike.scheduler.service.vo.V5RepairTaskDetailVo;
import com.paike.scheduler.service.vo.V5RepairTaskVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class V5RepairTaskFlowService {

    private static final List<String> TERMINAL_STATUSES = List.of(
            V5RepairTaskStatus.CANCELLED.getCode(),
            V5RepairTaskStatus.FAILED.getCode()
    );

    private final ScheduleRepairTaskMapper repairTaskMapper;
    private final SchedulePlanMapper schedulePlanMapper;
    private final ScheduleMapper scheduleMapper;
    private final V4ScheduleRiskService scheduleRiskService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public V5RepairTaskDetailVo createTask(V5RepairTaskFlowCreateRequest request) {
        if (request.getPlanId() == null && request.getSourceScheduleId() == null) {
            throw new BusinessException("修复任务至少需要绑定方案或正式课表");
        }

        SchedulePlan plan = null;
        if (request.getPlanId() != null) {
            plan = schedulePlanMapper.selectById(request.getPlanId());
            if (plan == null) throw new BusinessException("绑定方案不存在");
            if (!Objects.equals(plan.getSemesterId(), request.getSemesterId())) {
                throw new BusinessException("绑定方案不属于当前学期");
            }
        }

        Schedule schedule = null;
        if (request.getSourceScheduleId() != null) {
            schedule = scheduleMapper.selectById(request.getSourceScheduleId());
            if (schedule == null || Integer.valueOf(1).equals(schedule.getDeleted())) {
                throw new BusinessException("绑定正式课表记录不存在");
            }
            if (!Objects.equals(schedule.getSemesterId(), request.getSemesterId())) {
                throw new BusinessException("绑定正式课表不属于当前学期");
            }
        }

        Long effectivePlanId = request.getPlanId() != null ? request.getPlanId() : schedule == null ? null : schedule.getPlanId();
        if (effectivePlanId != null && request.getRiskItemIds() != null && !request.getRiskItemIds().isEmpty()) {
            validateRiskItems(effectivePlanId, request.getRiskItemIds());
        }

        ScheduleRepairTask task = new ScheduleRepairTask();
        task.setSemesterId(request.getSemesterId());
        task.setPlanId(effectivePlanId);
        task.setSourcePlanId(request.getSourcePlanId() != null ? request.getSourcePlanId() : (plan == null ? null : plan.getId()));
        task.setSourceScheduleId(request.getSourceScheduleId());
        task.setTaskCode("RPT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        task.setTitle(trimToNull(request.getTitle()));
        task.setTaskType(request.getTaskType().trim().toUpperCase(Locale.ROOT));
        task.setStatus(V5RepairTaskStatus.CREATED.getCode());
        task.setTriggerSource(trimToNull(request.getTriggerSource()) == null ? "MANUAL" : request.getTriggerSource().trim().toUpperCase(Locale.ROOT));
        task.setRiskTypes(writeJson(request.getRiskTypes() == null ? List.of() : request.getRiskTypes()));
        task.setRiskItemIds(writeJson(request.getRiskItemIds() == null ? List.of() : request.getRiskItemIds()));
        task.setScopePlanItemIds(writeJson(request.getScopePlanItemIds() == null ? List.of() : request.getScopePlanItemIds()));
        task.setTargetItemCount(request.getScopePlanItemIds() == null ? 0 : request.getScopePlanItemIds().size());
        task.setLockedItemCount(0);
        task.setProcessedItemCount(0);
        task.setSuccessItemCount(0);
        task.setFailureItemCount(0);
        repairTaskMapper.insert(task);
        return toDetail(task);
    }

    public List<V5RepairTaskVo> listTasks(Long semesterId, Long planId, String status) {
        LambdaQueryWrapper<ScheduleRepairTask> wrapper = new LambdaQueryWrapper<ScheduleRepairTask>()
                .orderByDesc(ScheduleRepairTask::getCreatedAt)
                .orderByDesc(ScheduleRepairTask::getId);
        if (semesterId != null) wrapper.eq(ScheduleRepairTask::getSemesterId, semesterId);
        if (planId != null) wrapper.eq(ScheduleRepairTask::getPlanId, planId);
        if (trimToNull(status) != null) wrapper.eq(ScheduleRepairTask::getStatus, status.trim().toUpperCase(Locale.ROOT));
        return repairTaskMapper.selectList(wrapper).stream().map(this::toVo).toList();
    }

    public V5RepairTaskDetailVo getTask(Long taskId) {
        ScheduleRepairTask task = requireTask(taskId);
        return toDetail(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public V5RepairTaskDetailVo updateStatus(Long taskId, V5RepairTaskStatusUpdateRequest request) {
        ScheduleRepairTask task = requireTask(taskId);
        String next = normalizeStatus(request.getStatus());
        validateStatusTransition(task.getStatus(), next);

        task.setStatus(next);
        if (V5RepairTaskStatus.ANALYZING.getCode().equals(next) && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        if (isTerminal(next)) {
            task.setFinishedAt(LocalDateTime.now());
        }
        if (V5RepairTaskStatus.FAILED.getCode().equals(next)) {
            task.setErrorMessage(trimToNull(request.getMessage()));
        }
        repairTaskMapper.updateById(task);
        return toDetail(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public V5RepairTaskDetailVo cancelTask(Long taskId, String cancelReason) {
        ScheduleRepairTask task = requireTask(taskId);
        if (isTerminal(task.getStatus())) {
            throw new BusinessException("已结束任务不能取消");
        }
        task.setStatus(V5RepairTaskStatus.CANCELLED.getCode());
        task.setCancelReason(trimToNull(cancelReason));
        task.setFinishedAt(LocalDateTime.now());
        repairTaskMapper.updateById(task);
        return toDetail(task);
    }

    public void ensureCanSimulate(Long taskId) {
        ScheduleRepairTask task = requireTask(taskId);
        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            throw new BusinessException("已取消或失败的修复任务不能继续生成试算方案，请重新创建任务");
        }
    }

    private void validateRiskItems(Long planId, List<Long> riskItemIds) {
        ScheduleRiskListVo risks = scheduleRiskService.getPlanRisks(planId, null, null, null);
        List<Long> exists = risks.getRisks().stream().map(ScheduleRiskIssueVo::getId).sorted(Comparator.naturalOrder()).toList();
        List<Long> missing = new ArrayList<>();
        for (Long id : riskItemIds) {
            if (id == null) continue;
            if (!exists.contains(id)) missing.add(id);
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("存在无效风险项ID：" + missing);
        }
    }

    private void validateStatusTransition(String current, String next) {
        if (Objects.equals(current, next)) return;
        if (isTerminal(current)) {
            throw new BusinessException("已取消或失败任务不能继续流转");
        }
        switch (next) {
            case "CREATED" -> throw new BusinessException("不允许回退到 CREATED");
            case "ANALYZING", "SUGGESTED", "SIMULATED", "APPLIED", "FAILED", "CANCELLED" -> {
            }
            default -> throw new BusinessException("不支持的修复任务状态：" + next);
        }
        if ("APPLIED".equals(next) && !"SIMULATED".equals(current) && !"SUGGESTED".equals(current)) {
            throw new BusinessException("仅试算或建议完成后可标记为 APPLIED");
        }
    }

    private String normalizeStatus(String status) {
        if (trimToNull(status) == null) throw new BusinessException("状态不能为空");
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isTerminal(String status) {
        return TERMINAL_STATUSES.contains(status) || V5RepairTaskStatus.APPLIED.getCode().equals(status);
    }

    private ScheduleRepairTask requireTask(Long taskId) {
        ScheduleRepairTask task = repairTaskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("修复任务不存在");
        return task;
    }

    private V5RepairTaskVo toVo(ScheduleRepairTask task) {
        V5RepairTaskVo vo = new V5RepairTaskVo();
        vo.setId(task.getId());
        vo.setSemesterId(task.getSemesterId());
        vo.setPlanId(task.getPlanId());
        vo.setTitle(task.getTitle());
        vo.setTaskCode(task.getTaskCode());
        vo.setTaskType(task.getTaskType());
        vo.setStatus(task.getStatus());
        vo.setResultPlanId(task.getResultPlanId());
        vo.setStartedAt(task.getStartedAt());
        vo.setFinishedAt(task.getFinishedAt());
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    private V5RepairTaskDetailVo toDetail(ScheduleRepairTask task) {
        V5RepairTaskDetailVo vo = new V5RepairTaskDetailVo();
        vo.setId(task.getId());
        vo.setSemesterId(task.getSemesterId());
        vo.setPlanId(task.getPlanId());
        vo.setSourcePlanId(task.getSourcePlanId());
        vo.setSourceScheduleId(task.getSourceScheduleId());
        vo.setResultPlanId(task.getResultPlanId());
        vo.setTaskCode(task.getTaskCode());
        vo.setTitle(task.getTitle());
        vo.setTaskType(task.getTaskType());
        vo.setStatus(task.getStatus());
        vo.setTriggerSource(task.getTriggerSource());
        vo.setRiskTypes(readStringList(task.getRiskTypes()));
        vo.setRiskItemIds(readLongList(task.getRiskItemIds()));
        vo.setScopePlanItemIds(readLongList(task.getScopePlanItemIds()));
        vo.setTargetItemCount(task.getTargetItemCount());
        vo.setLockedItemCount(task.getLockedItemCount());
        vo.setProcessedItemCount(task.getProcessedItemCount());
        vo.setSuccessItemCount(task.getSuccessItemCount());
        vo.setFailureItemCount(task.getFailureItemCount());
        vo.setStartedAt(task.getStartedAt());
        vo.setFinishedAt(task.getFinishedAt());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCancelReason(task.getCancelReason());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("修复任务数据序列化失败");
        }
    }

    private List<String> readStringList(String json) {
        if (trimToNull(json) == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("反序列化 List<String> 失败，使用空列表兜底；payload 前 200 字符: {}",
                    json.substring(0, Math.min(200, json.length())), e);
            return List.of();
        }
    }

    private List<Long> readLongList(String json) {
        if (trimToNull(json) == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("反序列化 List<Long> 失败，使用空列表兜底；payload 前 200 字符: {}",
                    json.substring(0, Math.min(200, json.length())), e);
            return List.of();
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

