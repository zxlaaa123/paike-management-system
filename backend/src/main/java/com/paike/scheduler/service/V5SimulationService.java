package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.SchedulePlanStatus;
import com.paike.scheduler.common.enums.V5RepairTaskStatus;
import com.paike.scheduler.common.enums.V5SuggestionStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleAdjustLog;
import com.paike.scheduler.entity.ScheduleOptimizationCompare;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleLockedItem;
import com.paike.scheduler.entity.ScheduleRepairSuggestion;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.ScheduleOptimizationCompareMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRepairSuggestionMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.mapper.ScheduleScoreDetailMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.dto.V5CandidateEvaluateRequest;
import com.paike.scheduler.service.dto.V5LocalReplanRequest;
import com.paike.scheduler.service.vo.ApplyPlanResultVo;
import com.paike.scheduler.service.vo.ScheduleAdjustLogVo;
import com.paike.scheduler.service.vo.SchedulePlanItemVo;
import com.paike.scheduler.service.vo.ScheduleRiskIssueVo;
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import com.paike.scheduler.service.vo.V5CandidateEvaluationVo;
import com.paike.scheduler.service.vo.V5LocalReplanSummaryVo;
import com.paike.scheduler.service.vo.V5SimulationCompareVo;
import com.paike.scheduler.service.vo.V5SimulationItemChangeVo;
import com.paike.scheduler.service.vo.V5SimulationLoadChangeVo;
import com.paike.scheduler.service.vo.V5SimulationPlanDetailVo;
import com.paike.scheduler.service.vo.V5SimulationRoomUtilizationChangeVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.paike.scheduler.common.util.StringSanitizer.trimToNull;

@Service
@RequiredArgsConstructor
public class V5SimulationService {

    private final ScheduleRepairTaskMapper repairTaskMapper;
    private final ScheduleRepairSuggestionMapper suggestionMapper;
    private final SchedulePlanMapper planMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final ScheduleScoreDetailMapper scoreDetailMapper;
    private final ScheduleLockedItemMapper lockedItemMapper;
    private final ScheduleOptimizationCompareMapper compareMapper;
    private final SchedulePlanService schedulePlanService;
    private final ScheduleScoreService scoreService;
    private final V4ScheduleRiskService riskService;
    private final V5RuleEvaluationService ruleEvaluationService;
    private final SchedulePlanExplainService explainService;
    private final V5ConsistencyCheckService consistencyCheckService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final SystemAuditLogService auditLogService;

    public V5SimulationPlanDetailVo generate(Long taskId, Long suggestionId) {
        try {
            Long simulationPlanId = runInTransaction(() -> generateInTransaction(taskId, suggestionId));
            return detail(taskId, simulationPlanId);
        } catch (RuntimeException ex) {
            auditLogService.recordFailure(
                    SystemAuditLogService.ACTION_GENERATE_SIMULATION_PLAN,
                    SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                    null,
                    null,
                    null,
                    SystemAuditLogService.auditErrorCode(ex),
                    ex.getMessage());
            throw ex;
        }
    }

    private Long generateInTransaction(Long taskId, Long suggestionId) {
        ScheduleRepairTask task = requireTask(taskId);
        if (isTerminal(task.getStatus())) {
            throw new BusinessException("已结束任务不能生成试算方案");
        }
        ScheduleRepairSuggestion suggestion = requireSuggestion(taskId, suggestionId);
        SuggestionMove move = readMove(suggestion);
        if (!move.executable()) {
            throw new BusinessException("当前建议缺少可执行目标位置，不能自动生成试算方案");
        }

        SchedulePlan baseline = resolveBaselinePlan(task);
        List<SchedulePlanItem> sourceItems = loadSourceItems(task, baseline);
        if (sourceItems.isEmpty()) {
            throw new BusinessException("试算基础没有课程明细");
        }
        Map<Long, SchedulePlanItem> sourceItemMap = sourceItems.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(SchedulePlanItem::getId, this::copyDetachedItem, (a, b) -> a, LinkedHashMap::new));

        SchedulePlan simulation = createSimulationPlan(task, suggestion, baseline, sourceItems);
        Map<Long, Long> copiedItemIds = copyItems(simulation, sourceItems);
        Long simulationItemId = copiedItemIds.get(suggestion.getSourcePlanItemId());
        if (simulationItemId == null) {
            throw new BusinessException("试算副本中找不到待修复课程");
        }

        SchedulePlanItem target = planItemMapper.selectById(simulationItemId);
        SchedulePlanItem before = sourceItemMap.get(suggestion.getSourcePlanItemId());
        target.setWeekday(move.targetWeekday());
        target.setStartPeriod(move.targetStartPeriod());
        target.setEndPeriod(move.targetEndPeriod());
        target.setClassroomId(move.targetClassroomId());
        target.setSourceType("SIMULATION");
        target.setUpdatedAt(LocalDateTime.now());
        planItemMapper.updateById(target);

        int conflictCount = schedulePlanService.refreshPlanConflictState(simulation.getId());
        simulation = planMapper.selectById(simulation.getId());
        simulation.setConflictCount(conflictCount);
        scoreService.rescore(simulation);
        simulation = planMapper.selectById(simulation.getId());

        ScheduleRiskListVo baselineRisks = baseline == null ? emptyRisks(null) : riskService.getPlanRisks(baseline.getId(), null, null, null);
        ScheduleRiskListVo simulationRisks = riskService.getPlanRisks(simulation.getId(), null, null, null);
        V5SimulationCompareVo compare = buildCompare(baseline, simulation, baselineRisks, simulationRisks, before, planItemMapper.selectById(simulationItemId));
        persistCompare(task, baseline, simulation, compare);

        suggestion.setStatus(V5SuggestionStatus.ACCEPTED.getCode());
        suggestionMapper.updateById(suggestion);
        task.setStatus(V5RepairTaskStatus.SIMULATED.getCode());
        task.setResultPlanId(simulation.getId());
        task.setProcessedItemCount(1);
        task.setSuccessItemCount(1);
        task.setFailureItemCount(0);
        task.setFinishedAt(LocalDateTime.now());
        repairTaskMapper.updateById(task);
        auditLogService.recordSuccess(
                SystemAuditLogService.ACTION_GENERATE_SIMULATION_PLAN,
                SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                simulation.getId(),
                simulation.getSemesterId(),
                simulation.getId(),
                "生成试算方案成功：修复任务 " + taskId + "，建议 " + suggestionId + "，方案 " + simulation.getId());

        return simulation.getId();
    }

    public V5SimulationPlanDetailVo localReplan(Long taskId, V5LocalReplanRequest request) {
        try {
            LocalReplanResult result = runInTransaction(() -> localReplanInTransaction(taskId, request));
            V5SimulationPlanDetailVo detail = detail(taskId, result.planId());
            detail.setLocalReplanSummary(result.summary());
            return detail;
        } catch (RuntimeException ex) {
            auditLogService.recordFailure(
                    SystemAuditLogService.ACTION_GENERATE_LOCAL_REPLAN_SIMULATION,
                    SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                    null,
                    null,
                    null,
                    SystemAuditLogService.auditErrorCode(ex),
                    ex.getMessage());
            throw ex;
        }
    }

    private LocalReplanResult localReplanInTransaction(Long taskId, V5LocalReplanRequest request) {
        ScheduleRepairTask task = requireTask(taskId);
        if (isTerminal(task.getStatus())) {
            throw new BusinessException("已结束任务不能生成局部重排试算方案");
        }
        SchedulePlan baseline = resolveBaselinePlan(task);
        if (baseline == null) {
            throw new BusinessException("局部重排必须基于原方案，不能直接基于正式课表全量重排");
        }

        V5LocalReplanRequest safeRequest = request == null ? new V5LocalReplanRequest() : request;
        List<SchedulePlanItem> sourceItems = loadSourceItems(task, baseline);
        if (sourceItems.isEmpty()) {
            throw new BusinessException("局部重排基础没有课程明细");
        }
        Map<Long, SchedulePlanItem> sourceItemMap = sourceItems.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(SchedulePlanItem::getId, this::copyDetachedItem, (a, b) -> a, LinkedHashMap::new));
        Set<Long> scopeIds = resolveLocalReplanScope(task, baseline, sourceItems, safeRequest);
        if (scopeIds.isEmpty()) {
            throw new BusinessException("局部重排范围为空，请选择班级、教师、教室、时间段、风险项或课程");
        }

        List<ScheduleLockedItem> locks = loadActiveLocks(baseline.getId());
        Set<Long> lockedIds = locks.stream()
                .map(ScheduleLockedItem::getPlanItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Long> replanableSourceIds = scopeIds.stream()
                .filter(id -> !lockedIds.contains(id))
                .filter(sourceItemMap::containsKey)
                .toList();
        if (replanableSourceIds.isEmpty()) {
            throw new BusinessException("局部重排范围内没有可移动课程，全部课程已锁定或不存在");
        }

        List<String> logs = new ArrayList<>();
        logs.add("局部重排开始：范围课程 " + scopeIds.size() + " 条，锁定课程 " + lockedIds.size() + " 条，可重排课程 " + replanableSourceIds.size() + " 条。");
        SchedulePlan simulation = createLocalReplanPlan(task, baseline, safeRequest, sourceItems, scopeIds, lockedIds);
        Map<Long, Long> copiedItemIds = copyItems(simulation, sourceItems);
        copyLocks(locks, copiedItemIds, simulation.getId());

        Set<Long> simulationScopeIds = scopeIds.stream()
                .map(copiedItemIds::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> movedItemIds = new ArrayList<>();
        List<Long> failedItemIds = new ArrayList<>();
        int candidateLimit = safeRequest.getCandidateLimit() == null || safeRequest.getCandidateLimit() <= 0
                ? 600
                : Math.min(safeRequest.getCandidateLimit(), 2000);

        for (Long sourceItemId : replanableSourceIds) {
            Long simulationItemId = copiedItemIds.get(sourceItemId);
            if (simulationItemId == null) {
                failedItemIds.add(sourceItemId);
                logs.add("课程 " + sourceItemId + " 未复制到试算方案，跳过。");
                continue;
            }
            SchedulePlanItem before = sourceItemMap.get(sourceItemId);
            SchedulePlanItem target = planItemMapper.selectById(simulationItemId);
            CandidatePlacement placement = findBestLocalPlacement(simulation.getId(), target, simulationScopeIds, candidateLimit);
            if (placement == null) {
                failedItemIds.add(sourceItemId);
                String reason = "未找到满足硬约束的候选位置";
                logs.add("课程 " + sourceItemId + " 重排失败：" + reason + "。");
                appendAdjustLog(simulation, target, before, target, BigDecimal.ZERO, reason);
                continue;
            }

            target.setWeekday(placement.weekday());
            target.setStartPeriod(placement.startPeriod());
            target.setEndPeriod(placement.endPeriod());
            target.setClassroomId(placement.classroomId());
            target.setSourceType("V5_LOCAL_REPLAN");
            target.setUpdatedAt(LocalDateTime.now());
            planItemMapper.updateById(target);

            SchedulePlanItem after = planItemMapper.selectById(simulationItemId);
            if (hasPlacementChanged(before, after)) {
                movedItemIds.add(sourceItemId);
                logs.add("课程 " + sourceItemId + " 已移动到 周" + after.getWeekday() + " 第" + after.getStartPeriod() + "-" + after.getEndPeriod() + "节，评分变化 " + placement.score() + "。");
                appendAdjustLog(simulation, after, before, after, placement.score(), "V5局部重排：选择软约束评分最优可用位置");
            } else {
                logs.add("课程 " + sourceItemId + " 保持原位置：原位置仍为当前最优可用位置。");
            }
        }

        int conflictCount = schedulePlanService.refreshPlanConflictState(simulation.getId());
        simulation = planMapper.selectById(simulation.getId());
        simulation.setConflictCount(conflictCount);
        scoreService.rescore(simulation);
        simulation = planMapper.selectById(simulation.getId());

        ScheduleRiskListVo baselineRisks = riskService.getPlanRisks(baseline.getId(), null, null, null);
        ScheduleRiskListVo simulationRisks = riskService.getPlanRisks(simulation.getId(), null, null, null);
        V5SimulationCompareVo compare = buildCompare(baseline, simulation, baselineRisks, simulationRisks, null, null);
        if (Boolean.TRUE.equals(compare.getHasNewHardConflicts())) {
            throw new BusinessException("局部重排结果引入新的硬冲突，已回滚。请缩小范围或增加可用教室/时间段");
        }
        persistCompare(task, baseline, simulation, compare);

        task.setStatus(V5RepairTaskStatus.SIMULATED.getCode());
        task.setResultPlanId(simulation.getId());
        task.setLockedItemCount(lockedIds.size());
        task.setTargetItemCount(scopeIds.size());
        task.setProcessedItemCount(replanableSourceIds.size());
        task.setSuccessItemCount(Math.max(0, replanableSourceIds.size() - failedItemIds.size()));
        task.setFailureItemCount(failedItemIds.size());
        task.setFinishedAt(LocalDateTime.now());
        repairTaskMapper.updateById(task);

        logs.add("局部重排完成：移动 " + movedItemIds.size() + " 条，失败 " + failedItemIds.size() + " 条；生成试算方案 " + simulation.getId() + "，未写入正式课表。");
        V5LocalReplanSummaryVo summary = new V5LocalReplanSummaryVo();
        summary.setScopeItemCount(scopeIds.size());
        summary.setLockedCount(lockedIds.size());
        summary.setReplanableCount(replanableSourceIds.size());
        summary.setMovedCount(movedItemIds.size());
        summary.setFailedCount(failedItemIds.size());
        summary.setMovedItemIds(movedItemIds);
        summary.setFailedItemIds(failedItemIds);
        summary.setLogs(logs);
        auditLogService.recordSuccess(
                SystemAuditLogService.ACTION_GENERATE_LOCAL_REPLAN_SIMULATION,
                SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                simulation.getId(),
                simulation.getSemesterId(),
                simulation.getId(),
                "生成局部重排试算方案成功：修复任务 " + taskId + "，方案 " + simulation.getId());

        return new LocalReplanResult(simulation.getId(), summary);
    }

    public V5SimulationPlanDetailVo detail(Long taskId, Long planId) {
        ScheduleRepairTask task = requireTask(taskId);
        SchedulePlan plan = requireSimulationPlan(task, planId);
        SchedulePlan baseline = resolveCompareBaseline(task, plan);
        ScheduleRiskListVo risks = riskService.getPlanRisks(plan.getId(), null, null, null);
        ScheduleRiskListVo baselineRisks = baseline == null ? emptyRisks(null) : riskService.getPlanRisks(baseline.getId(), null, null, null);
        ItemPair changedItem = resolveAcceptedSuggestionChange(task, plan);

        V5SimulationPlanDetailVo vo = new V5SimulationPlanDetailVo();
        vo.setPlan(plan);
        vo.setItems(schedulePlanService.getPlanItems(plan.getId()));
        vo.setScoreDetails(scoreDetailMapper.selectList(new LambdaQueryWrapper<ScheduleScoreDetail>()
                .eq(ScheduleScoreDetail::getPlanId, plan.getId())
                .orderByAsc(ScheduleScoreDetail::getRuleCode)));
        List<ScheduleAdjustLogVo> adjustLogs = explainService.listAdjustLogs(plan.getSemesterId(), plan.getId(), null, 1, 500).getRecords();
        vo.setAdjustLogs(adjustLogs);
        vo.setRisks(risks);
        vo.setCompare(buildCompare(baseline, plan, baselineRisks, risks, changedItem.before(), changedItem.after()));
        vo.setLocalReplanSummary(buildPersistedLocalReplanSummary(task, plan, adjustLogs));
        try {
            vo.setLatestConsistencyReport(consistencyCheckService.latest(taskId, plan.getId()));
        } catch (Exception ignored) {
            // 历史报告读取失败不影响详情返回
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public V5SimulationPlanDetailVo confirm(Long taskId, Long planId) {
        ScheduleRepairTask task = requireTask(taskId);
        SchedulePlan plan = requireSimulationPlan(task, planId);
        if (!SchedulePlanStatus.SIMULATION.is(plan.getStatus())) {
            throw new BusinessException("只有试算方案可以确认");
        }
        plan.setStatus(SchedulePlanStatus.CONFIRMED.getCode());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return detail(taskId, planId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplyPlanResultVo apply(Long taskId, Long planId) {
        ScheduleRepairTask task = null;
        SchedulePlan plan = null;
        try {
            task = requireTask(taskId);
            plan = requireSimulationPlan(task, planId);
            if (!SchedulePlanStatus.SIMULATION.is(plan.getStatus()) && !SchedulePlanStatus.CONFIRMED.is(plan.getStatus())) {
                throw new BusinessException("只有试算或已确认方案可以应用");
            }
            // apply gate：强制后端重跑一致性校验，存在 BLOCKING 时阻止应用
            consistencyCheckService.ensurePassBeforeApply(taskId, planId);
            schedulePlanService.refreshPlanConflictState(planId);
            scoreService.rescore(planMapper.selectById(planId));
            plan = planMapper.selectById(planId);
            SchedulePlan baseline = resolveCompareBaseline(task, plan);
            ScheduleRiskListVo risks = riskService.getPlanRisks(plan.getId(), null, null, null);
            ScheduleRiskListVo baselineRisks = baseline == null ? emptyRisks(null) : riskService.getPlanRisks(baseline.getId(), null, null, null);
            ItemPair changedItem = resolveAcceptedSuggestionChange(task, plan);
            V5SimulationCompareVo compare = buildCompare(baseline, plan, baselineRisks, risks, changedItem.before(), changedItem.after());
            if (plan.getConflictCount() != null && plan.getConflictCount() > 0) {
                throw new BusinessException("试算方案仍存在冲突，不能应用");
            }
            if (Boolean.TRUE.equals(compare.getHasNewHardConflicts())) {
                throw new BusinessException("试算方案引入新的硬冲突，不推荐应用");
            }
            if (SchedulePlanStatus.SIMULATION.is(plan.getStatus())) {
                plan.setStatus(SchedulePlanStatus.CONFIRMED.getCode());
                plan.setUpdatedAt(LocalDateTime.now());
                planMapper.updateById(plan);
            }
            ApplyPlanResultVo result = schedulePlanService.applySimulationPlan(planId);
            plan = planMapper.selectById(planId);
            if (plan != null && !SchedulePlanStatus.APPLIED.is(plan.getStatus())) {
                plan.setStatus(SchedulePlanStatus.APPLIED.getCode());
                plan.setUpdatedAt(LocalDateTime.now());
                planMapper.updateById(plan);
            }
            task.setStatus(V5RepairTaskStatus.APPLIED.getCode());
            task.setResultPlanId(planId);
            task.setFinishedAt(LocalDateTime.now());
            repairTaskMapper.updateById(task);
            auditLogService.recordSuccess(
                    SystemAuditLogService.ACTION_APPLY_SIMULATION_PLAN,
                    SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                    planId,
                    plan == null ? null : plan.getSemesterId(),
                    planId,
                    "应用试算方案成功：修复任务 " + taskId + "，方案 " + planId);
            return result;
        } catch (RuntimeException ex) {
            auditLogService.recordFailure(
                    SystemAuditLogService.ACTION_APPLY_SIMULATION_PLAN,
                    SystemAuditLogService.TARGET_SCHEDULE_PLAN,
                    planId,
                    plan == null ? (task == null ? null : task.getSemesterId()) : plan.getSemesterId(),
                    planId,
                    SystemAuditLogService.auditErrorCode(ex),
                    ex.getMessage());
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public V5SimulationPlanDetailVo discard(Long taskId, Long planId) {
        ScheduleRepairTask task = requireTask(taskId);
        SchedulePlan plan = requireSimulationPlan(task, planId);
        if (SchedulePlanStatus.APPLIED.is(plan.getStatus())) {
            throw new BusinessException("已应用试算方案不能放弃");
        }
        if (SchedulePlanStatus.DISCARDED.is(plan.getStatus())) {
            throw new BusinessException("试算方案已放弃，不能重复操作");
        }
        plan.setStatus(SchedulePlanStatus.DISCARDED.getCode());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);

        if (Objects.equals(task.getResultPlanId(), planId)) {
            task.setStatus(V5RepairTaskStatus.SUGGESTED.getCode());
            task.setResultPlanId(null);
            task.setUpdatedAt(LocalDateTime.now());
            repairTaskMapper.updateById(task);
        }

        // P2-15: 先生成详情快照，再清理孤儿数据；避免 V4ScheduleRiskService 对空 items 的空 IN 报错
        V5SimulationPlanDetailVo result = detail(taskId, planId);

        // 清理试算副本的孤儿数据；保留 optimization_compare 与 adjust_log 作为审计快照
        planItemMapper.delete(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, planId));
        lockedItemMapper.update(null, new LambdaUpdateWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getPlanId, planId)
                .eq(ScheduleLockedItem::getActiveFlag, 1)
                .set(ScheduleLockedItem::getActiveFlag, 0)
                .set(ScheduleLockedItem::getUpdatedAt, LocalDateTime.now()));
        scoreDetailMapper.delete(new LambdaQueryWrapper<ScheduleScoreDetail>()
                .eq(ScheduleScoreDetail::getPlanId, planId));

        return result;
    }

    private SchedulePlan createSimulationPlan(
            ScheduleRepairTask task,
            ScheduleRepairSuggestion suggestion,
            SchedulePlan baseline,
            List<SchedulePlanItem> sourceItems
    ) {
        LocalDateTime now = LocalDateTime.now();
        SchedulePlan plan = new SchedulePlan();
        plan.setSemesterId(task.getSemesterId());
        plan.setSourcePlanId(baseline == null ? task.getSourcePlanId() : baseline.getId());
        plan.setSourceScheduleId(task.getSourceScheduleId());
        plan.setRepairTaskId(task.getId());
        plan.setName("试算方案-" + task.getTaskCode() + "-" + suggestion.getSuggestionCode());
        plan.setStrategyType(baseline == null ? "V5_SIMULATION" : baseline.getStrategyType());
        plan.setPlanMode("SIMULATION");
        plan.setStatus(SchedulePlanStatus.SIMULATION.getCode());
        plan.setTotalScore(baseline == null ? BigDecimal.ZERO : baseline.getTotalScore());
        plan.setScheduledCount(sourceItems.size());
        plan.setUnscheduledCount(baseline == null ? 0 : baseline.getUnscheduledCount());
        plan.setConflictCount(0);
        plan.setDescription("由修复建议 " + suggestion.getSuggestionCode() + " 生成的试算方案");
        plan.setGeneratedBy("V5_SIMULATION");
        plan.setGeneratedAt(now);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        planMapper.insert(plan);
        return plan;
    }

    private SchedulePlan createLocalReplanPlan(
            ScheduleRepairTask task,
            SchedulePlan baseline,
            V5LocalReplanRequest request,
            List<SchedulePlanItem> sourceItems,
            Set<Long> scopeIds,
            Set<Long> lockedIds
    ) {
        LocalDateTime now = LocalDateTime.now();
        SchedulePlan plan = new SchedulePlan();
        plan.setSemesterId(task.getSemesterId());
        plan.setSourcePlanId(baseline.getId());
        plan.setSourceScheduleId(task.getSourceScheduleId());
        plan.setRepairTaskId(task.getId());
        plan.setName(resolveLocalReplanName(request.getNewPlanName(), task, baseline));
        plan.setStrategyType(baseline.getStrategyType());
        plan.setPlanMode("SIMULATION");
        plan.setStatus(SchedulePlanStatus.SIMULATION.getCode());
        plan.setTotalScore(baseline.getTotalScore());
        plan.setScheduledCount(sourceItems.size());
        plan.setUnscheduledCount(baseline.getUnscheduledCount());
        plan.setConflictCount(0);
        plan.setDescription("V5阶段8局部重排试算方案；来源方案ID=" + baseline.getId()
                + "；范围课程=" + scopeIds.size()
                + "；锁定课程=" + lockedIds.size()
                + "；只移动范围内未锁定课程，不直接覆盖正式课表。");
        plan.setGeneratedBy("V5_LOCAL_REPLAN");
        plan.setGeneratedAt(now);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        planMapper.insert(plan);
        return plan;
    }

    private Set<Long> resolveLocalReplanScope(
            ScheduleRepairTask task,
            SchedulePlan baseline,
            List<SchedulePlanItem> sourceItems,
            V5LocalReplanRequest request
    ) {
        Set<Long> scope = new LinkedHashSet<>();
        addAll(scope, readLongList(task.getScopePlanItemIds()));
        addAll(scope, request.getSelectedPlanItemIds());
        addRiskScope(scope, baseline, task, request);

        Set<Long> classIds = toLongSet(request.getClassIds());
        Set<Long> teacherIds = toLongSet(request.getTeacherIds());
        Set<Long> classroomIds = toLongSet(request.getClassroomIds());
        Set<Integer> weekdays = request.getWeekdays() == null
                ? Set.of()
                : request.getWeekdays().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> periodNos = request.getPeriodNos() == null
                ? Set.of()
                : request.getPeriodNos().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!classIds.isEmpty() || !teacherIds.isEmpty() || !classroomIds.isEmpty() || !weekdays.isEmpty() || !periodNos.isEmpty()) {
            for (SchedulePlanItem item : sourceItems) {
                if (!classIds.isEmpty() && classIds.contains(item.getClassId())) scope.add(item.getId());
                if (!teacherIds.isEmpty() && teacherIds.contains(item.getTeacherId())) scope.add(item.getId());
                if (!classroomIds.isEmpty() && classroomIds.contains(item.getClassroomId())) scope.add(item.getId());
                if (!weekdays.isEmpty() && weekdays.contains(item.getWeekday())) scope.add(item.getId());
                if (!periodNos.isEmpty() && periodNos.contains(toPeriodNo(item.getStartPeriod()))) scope.add(item.getId());
            }
        }
        Set<Long> validIds = sourceItems.stream().map(SchedulePlanItem::getId).collect(Collectors.toSet());
        scope.removeIf(id -> id == null || !validIds.contains(id));
        return scope;
    }

    private void addRiskScope(Set<Long> scope, SchedulePlan baseline, ScheduleRepairTask task, V5LocalReplanRequest request) {
        Set<Long> riskIds = new LinkedHashSet<>();
        addAll(riskIds, readLongList(task.getRiskItemIds()));
        addAll(riskIds, request.getRiskItemIds());
        if (riskIds.isEmpty()) return;
        ScheduleRiskListVo risks = riskService.getPlanRisks(baseline.getId(), null, null, null);
        if (risks == null || risks.getRisks() == null) return;
        for (ScheduleRiskIssueVo risk : risks.getRisks()) {
            if (riskIds.contains(risk.getId())) {
                addAll(scope, risk.getRelatedItemIds());
            }
        }
    }

    private CandidatePlacement findBestLocalPlacement(Long planId, SchedulePlanItem target, Set<Long> simulationScopeIds, int candidateLimit) {
        List<TimeSlot> timeSlots = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
                .orderByAsc(TimeSlot::getDayOfWeek)
                .orderByAsc(TimeSlot::getPeriodNo));
        List<Classroom> classrooms = classroomMapper.selectList(new LambdaQueryWrapper<Classroom>()
                .eq(Classroom::getStatus, 1)
                .orderByAsc(Classroom::getRoomName));
        // P1-13: 把 plan / item / teacher / classInfo / course / allItems / weights / isLocked / classrooms / timeSlots 提到循环外。
        // 单次评估 SQL 从 ~11 降到 ~1（仅剩 isUnavailable）。
        V5RuleEvaluationService.EvaluationContext evalContext =
                ruleEvaluationService.buildEvaluationContext(planId, target.getId(), classrooms, timeSlots);
        CandidatePlacement best = null;
        int evaluated = 0;
        for (TimeSlot slot : timeSlots) {
            Integer start = slot.getPeriodNo() * 2 - 1;
            Integer end = start + 1;
            for (Classroom room : classrooms) {
                if (evaluated >= candidateLimit) return best;
                V5CandidateEvaluateRequest evalReq = new V5CandidateEvaluateRequest();
                evalReq.setPlanId(planId);
                evalReq.setPlanItemId(target.getId());
                evalReq.setCandidateWeekday(slot.getDayOfWeek());
                evalReq.setCandidateStartPeriod(start);
                evalReq.setCandidateEndPeriod(end);
                evalReq.setCandidateClassroomId(room.getId());
                evalReq.setScopePlanItemIds(new ArrayList<>(simulationScopeIds));
                evalReq.setSimulationOnly(true);
                evalReq.setSourcePlanId(planId);
                V5CandidateEvaluationVo eval = ruleEvaluationService.evaluateCandidate(evalReq, evalContext);
                evaluated++;
                if (!Boolean.TRUE.equals(eval.getAvailable())) {
                    continue;
                }
                CandidatePlacement candidate = new CandidatePlacement(
                        room.getId(),
                        slot.getDayOfWeek(),
                        start,
                        end,
                        eval.getTotalScoreDelta() == null ? BigDecimal.ZERO : eval.getTotalScoreDelta()
                );
                if (best == null || compareCandidate(candidate, best) > 0) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private int compareCandidate(CandidatePlacement a, CandidatePlacement b) {
        int score = a.score().compareTo(b.score());
        if (score != 0) return score;
        int day = Integer.compare(nullSafe(b.weekday()), nullSafe(a.weekday()));
        if (day != 0) return day;
        return Integer.compare(nullSafe(b.startPeriod()), nullSafe(a.startPeriod()));
    }

    private void copyLocks(List<ScheduleLockedItem> locks, Map<Long, Long> copiedItemIds, Long newPlanId) {
        Set<Long> copiedTargets = new LinkedHashSet<>();
        for (ScheduleLockedItem source : locks) {
            Long newItemId = copiedItemIds.get(source.getPlanItemId());
            if (newItemId == null) continue;
            if (!copiedTargets.add(newItemId)) continue;
            Long existing = lockedItemMapper.selectCount(new LambdaQueryWrapper<ScheduleLockedItem>()
                    .eq(ScheduleLockedItem::getTargetType, "PLAN")
                    .eq(ScheduleLockedItem::getPlanId, newPlanId)
                    .eq(ScheduleLockedItem::getPlanItemId, newItemId)
                    .eq(ScheduleLockedItem::getActiveFlag, 1));
            if (existing != null && existing > 0) continue;
            ScheduleLockedItem target = new ScheduleLockedItem();
            target.setTargetType("PLAN");
            target.setPlanId(newPlanId);
            target.setPlanItemId(newItemId);
            target.setScheduleId(null);
            target.setLockReason(source.getLockReason());
            target.setActiveFlag(1);
            target.setCreatedAt(LocalDateTime.now());
            target.setUpdatedAt(LocalDateTime.now());
            lockedItemMapper.insert(target);
        }
    }

    private List<ScheduleLockedItem> loadActiveLocks(Long planId) {
        return lockedItemMapper.selectList(new LambdaQueryWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getPlanId, planId)
                .eq(ScheduleLockedItem::getActiveFlag, 1)
                .isNotNull(ScheduleLockedItem::getPlanItemId)
                .orderByAsc(ScheduleLockedItem::getId));
    }

    private void appendAdjustLog(SchedulePlan plan, SchedulePlanItem item, SchedulePlanItem before, SchedulePlanItem after, BigDecimal score, String reason) {
        ScheduleAdjustLog log = new ScheduleAdjustLog();
        log.setPlanId(plan.getId());
        log.setSemesterId(plan.getSemesterId());
        log.setTeachingTaskId(item.getTeachingTaskId());
        log.setOldClassroomId(before == null ? null : before.getClassroomId());
        log.setOldWeekday(before == null ? null : before.getWeekday());
        log.setOldStartPeriod(before == null ? null : before.getStartPeriod());
        log.setOldEndPeriod(before == null ? null : before.getEndPeriod());
        log.setNewClassroomId(after == null ? null : after.getClassroomId());
        log.setNewWeekday(after == null ? null : after.getWeekday());
        log.setNewStartPeriod(after == null ? null : after.getStartPeriod());
        log.setNewEndPeriod(after == null ? null : after.getEndPeriod());
        log.setBeforeScore(before == null ? BigDecimal.ZERO : before.getScore());
        log.setAfterScore(score);
        log.setConflictFlag(after == null ? 0 : after.getConflictFlag());
        log.setAdjustReason(reason);
        explainService.appendAdjustLog(log);
    }

    private V5LocalReplanSummaryVo buildPersistedLocalReplanSummary(ScheduleRepairTask task, SchedulePlan plan, List<ScheduleAdjustLogVo> adjustLogs) {
        if (!"V5_LOCAL_REPLAN".equals(plan.getGeneratedBy())) {
            return null;
        }
        List<ScheduleAdjustLogVo> logs = adjustLogs == null ? List.of() : adjustLogs;
        List<Long> movedItemIds = logs.stream()
                .map(ScheduleAdjustLogVo::getTeachingTaskId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> messages = new ArrayList<>();
        messages.add("局部重排试算方案：" + plan.getName() + "，来源方案 " + plan.getSourcePlanId() + "，未写入正式课表。");
        messages.add("范围课程 " + intValue(task.getTargetItemCount()) + " 条，锁定课程 " + intValue(task.getLockedItemCount()) + " 条，可重排课程 " + intValue(task.getProcessedItemCount()) + " 条。");
        for (ScheduleAdjustLogVo log : logs) {
            messages.add((log.getAdjustReason() == null ? "局部重排调整" : log.getAdjustReason())
                    + "：周" + log.getOldWeekday() + " 第" + log.getOldStartPeriod() + "-" + log.getOldEndPeriod()
                    + "节 -> 周" + log.getNewWeekday() + " 第" + log.getNewStartPeriod() + "-" + log.getNewEndPeriod() + "节。");
        }
        if (logs.isEmpty()) {
            messages.add("当前局部重排未移动课程：原位置已是满足约束的可用位置，或范围内课程无需调整。");
        }

        V5LocalReplanSummaryVo summary = new V5LocalReplanSummaryVo();
        summary.setScopeItemCount(intValue(task.getTargetItemCount()));
        summary.setLockedCount(intValue(task.getLockedItemCount()));
        summary.setReplanableCount(intValue(task.getProcessedItemCount()));
        summary.setMovedCount(logs.size());
        summary.setFailedCount(intValue(task.getFailureItemCount()));
        summary.setMovedItemIds(movedItemIds);
        summary.setFailedItemIds(List.of());
        summary.setLogs(messages);
        return summary;
    }

    private String resolveLocalReplanName(String requestedName, ScheduleRepairTask task, SchedulePlan baseline) {
        String trimmed = trimToNull(requestedName);
        if (trimmed != null) return trimmed;
        return "局部重排试算-" + task.getTaskCode() + "-" + baseline.getId();
    }

    private void addAll(Set<Long> target, List<Long> values) {
        if (values == null) return;
        values.stream().filter(Objects::nonNull).forEach(target::add);
    }

    private Set<Long> toLongSet(List<Long> values) {
        if (values == null) return Set.of();
        return values.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Integer toPeriodNo(Integer startPeriod) {
        if (startPeriod == null || startPeriod <= 0) return null;
        return (startPeriod + 1) / 2;
    }

    private List<Long> readLongList(String json) {
        if (trimToNull(json) == null) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) return List.of();
            List<Long> result = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.canConvertToLong()) {
                    result.add(item.asLong());
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private int nullSafe(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private Map<Long, Long> copyItems(SchedulePlan simulation, List<SchedulePlanItem> sourceItems) {
        Map<Long, Long> copiedIds = new LinkedHashMap<>();
        for (SchedulePlanItem source : sourceItems) {
            SchedulePlanItem copy = copyDetachedItem(source);
            Long sourceId = copy.getId();
            copy.setId(null);
            copy.setPlanId(simulation.getId());
            copy.setSemesterId(simulation.getSemesterId());
            copy.setSourceType("SIMULATION");
            copy.setCreatedAt(LocalDateTime.now());
            copy.setUpdatedAt(LocalDateTime.now());
            planItemMapper.insert(copy);
            copiedIds.put(sourceId, copy.getId());
        }
        return copiedIds;
    }

    private List<SchedulePlanItem> loadSourceItems(ScheduleRepairTask task, SchedulePlan baseline) {
        if (baseline != null) {
            return planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                    .eq(SchedulePlanItem::getPlanId, baseline.getId())
                    .orderByAsc(SchedulePlanItem::getWeekday)
                    .orderByAsc(SchedulePlanItem::getStartPeriod));
        }
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, task.getSemesterId()));
        Map<Long, TimeSlot> slotMap = timeSlotMapper.selectBatchIds(schedules.stream()
                        .map(Schedule::getTimeSlotId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(TimeSlot::getId, slot -> slot, (a, b) -> a));
        List<SchedulePlanItem> items = new ArrayList<>();
        for (Schedule schedule : schedules) {
            TimeSlot slot = slotMap.get(schedule.getTimeSlotId());
            if (slot == null) continue;
            SchedulePlanItem item = new SchedulePlanItem();
            item.setId(schedule.getId());
            item.setSemesterId(schedule.getSemesterId());
            item.setTeachingTaskId(schedule.getTeachingTaskId());
            item.setTeacherId(schedule.getTeacherId());
            item.setClassId(schedule.getClassId());
            item.setCourseId(schedule.getCourseId());
            item.setClassroomId(schedule.getClassroomId());
            item.setWeekday(slot.getDayOfWeek());
            item.setStartPeriod(slot.getPeriodNo() * 2 - 1);
            item.setEndPeriod(slot.getPeriodNo() * 2);
            item.setWeekType("ALL");
            item.setScore(BigDecimal.ZERO);
            item.setConflictFlag(0);
            item.setSourceType("SCHEDULE");
            items.add(item);
        }
        return items;
    }

    private SchedulePlan resolveBaselinePlan(ScheduleRepairTask task) {
        Long planId = task.getPlanId() != null ? task.getPlanId() : task.getSourcePlanId();
        if (planId == null) return null;
        SchedulePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("试算来源方案不存在");
        }
        if (SchedulePlanStatus.SIMULATION.is(plan.getStatus()) || SchedulePlanStatus.DISCARDED.is(plan.getStatus())) {
            throw new BusinessException("不能基于试算或已放弃方案继续生成试算");
        }
        return plan;
    }

    private V5SimulationCompareVo buildCompare(
            SchedulePlan baseline,
            SchedulePlan simulation,
            ScheduleRiskListVo baselineRisks,
            ScheduleRiskListVo simulationRisks,
            SchedulePlanItem before,
            SchedulePlanItem after
    ) {
        ensureComparableSemester(baseline, simulation);
        List<SchedulePlanItemVo> baselineItems = loadCompareItems(baseline);
        List<SchedulePlanItemVo> simulationItems = loadCompareItems(simulation);
        Map<Long, SchedulePlanItemVo> baselineTaskMap = indexByTeachingTaskId(baselineItems);
        Map<Long, SchedulePlanItemVo> simulationTaskMap = indexByTeachingTaskId(simulationItems);
        Map<Long, String> classroomNames = loadClassroomNames(baselineItems, simulationItems, before, after);
        Set<Long> changedLockedItemIds = findChangedLockedItemIds(baseline, baselineTaskMap, simulationTaskMap);
        List<V5SimulationItemChangeVo> changedItems = buildChangedItems(baselineTaskMap, simulationTaskMap, before, after, classroomNames);

        V5SimulationCompareVo vo = new V5SimulationCompareVo();
        vo.setBaselineSemesterId(baseline == null ? null : baseline.getSemesterId());
        vo.setSimulationSemesterId(simulation.getSemesterId());
        vo.setBaselinePlanId(baseline == null ? null : baseline.getId());
        vo.setSimulationPlanId(simulation.getId());
        vo.setBaselineSourceScheduleId(baseline == null ? null : baseline.getSourceScheduleId());
        vo.setBaselinePlanName(baseline == null ? "正式课表" : baseline.getName());
        vo.setSimulationPlanName(simulation.getName());
        vo.setBaselineScore(scoreOf(baseline));
        vo.setSimulationScore(scoreOf(simulation));
        vo.setScoreDelta(vo.getSimulationScore().subtract(vo.getBaselineScore()));
        vo.setBaselineScheduledCount(intValue(baseline == null ? null : baseline.getScheduledCount()));
        vo.setSimulationScheduledCount(intValue(simulation.getScheduledCount()));
        vo.setScheduledDelta(vo.getSimulationScheduledCount() - vo.getBaselineScheduledCount());
        vo.setBaselineUnscheduledCount(intValue(baseline == null ? null : baseline.getUnscheduledCount()));
        vo.setSimulationUnscheduledCount(intValue(simulation.getUnscheduledCount()));
        vo.setUnscheduledDelta(vo.getSimulationUnscheduledCount() - vo.getBaselineUnscheduledCount());
        vo.setBaselineRiskCount(countRisks(baselineRisks));
        vo.setSimulationRiskCount(countRisks(simulationRisks));
        vo.setRiskDelta(vo.getSimulationRiskCount() - vo.getBaselineRiskCount());
        vo.setBaselineHighRiskCount(riskLevelCount(baselineRisks == null ? null : baselineRisks.getHighRiskCount()));
        vo.setSimulationHighRiskCount(riskLevelCount(simulationRisks == null ? null : simulationRisks.getHighRiskCount()));
        vo.setHighRiskDelta(vo.getSimulationHighRiskCount() - vo.getBaselineHighRiskCount());
        vo.setBaselineMediumRiskCount(riskLevelCount(baselineRisks == null ? null : baselineRisks.getMediumRiskCount()));
        vo.setSimulationMediumRiskCount(riskLevelCount(simulationRisks == null ? null : simulationRisks.getMediumRiskCount()));
        vo.setMediumRiskDelta(vo.getSimulationMediumRiskCount() - vo.getBaselineMediumRiskCount());
        vo.setBaselineLowRiskCount(riskLevelCount(baselineRisks == null ? null : baselineRisks.getLowRiskCount()));
        vo.setSimulationLowRiskCount(riskLevelCount(simulationRisks == null ? null : simulationRisks.getLowRiskCount()));
        vo.setLowRiskDelta(vo.getSimulationLowRiskCount() - vo.getBaselineLowRiskCount());
        vo.setBaselineConflictCount(baseline == null || baseline.getConflictCount() == null ? 0 : baseline.getConflictCount());
        vo.setSimulationConflictCount(simulation.getConflictCount() == null ? 0 : simulation.getConflictCount());
        vo.setConflictDelta(vo.getSimulationConflictCount() - vo.getBaselineConflictCount());
        vo.setCourseChangeCount(changedItems.size());
        vo.setChangedItems(changedItems);
        vo.setLockedCoursesPreserved(changedLockedItemIds.isEmpty());
        vo.setChangedLockedCourseNames(resolveLockedCourseNames(baselineTaskMap, changedLockedItemIds));
        vo.setNewRisks(diffRisks(simulationRisks, baselineRisks));
        vo.setResolvedRisks(diffRisks(baselineRisks, simulationRisks));
        vo.setTeacherLoadChanges(buildLoadChanges(baselineItems, simulationItems, SchedulePlanItemVo::getTeacherId, SchedulePlanItemVo::getTeacherName));
        vo.setClassLoadChanges(buildLoadChanges(baselineItems, simulationItems, SchedulePlanItemVo::getClassId, SchedulePlanItemVo::getClassName));
        vo.setRoomUtilizationChanges(buildRoomUtilizationChanges(baselineItems, simulationItems, classroomNames));
        int newHardConflictCount = Math.max(0, vo.getSimulationConflictCount() - vo.getBaselineConflictCount());
        vo.setNewHardConflictCount(newHardConflictCount);
        vo.setHasNewHardConflicts(newHardConflictCount > 0);
        boolean recommended = newHardConflictCount == 0 && changedLockedItemIds.isEmpty();
        vo.setRecommended(recommended);
        vo.setRecommendationMessage(buildRecommendationMessage(vo));
        vo.setSummary(buildSummary(vo));
        return vo;
    }

    private V5SimulationItemChangeVo buildItemChange(
            SchedulePlanItemVo before,
            SchedulePlanItemVo after,
            Map<Long, String> classroomNames
    ) {
        V5SimulationItemChangeVo vo = new V5SimulationItemChangeVo();
        vo.setSourceItemId(before.getId());
        vo.setSimulationItemId(after.getId());
        vo.setCourseName(after.getCourseName());
        vo.setTeacherName(after.getTeacherName());
        vo.setClassName(after.getClassName());
        vo.setBeforeWeekday(before.getWeekday());
        vo.setBeforeStartPeriod(before.getStartPeriod());
        vo.setBeforeEndPeriod(before.getEndPeriod());
        vo.setBeforeClassroomId(before.getClassroomId());
        vo.setBeforeClassroomName(classroomName(before.getClassroomId(), classroomNames));
        vo.setAfterWeekday(after.getWeekday());
        vo.setAfterStartPeriod(after.getStartPeriod());
        vo.setAfterEndPeriod(after.getEndPeriod());
        vo.setAfterClassroomId(after.getClassroomId());
        vo.setAfterClassroomName(classroomName(after.getClassroomId(), classroomNames));
        vo.setConflictFlag(after.getConflictFlag());
        vo.setConflictReason(after.getConflictReason());
        return vo;
    }

    private ItemPair resolveAcceptedSuggestionChange(ScheduleRepairTask task, SchedulePlan simulation) {
        ScheduleRepairSuggestion suggestion = suggestionMapper.selectOne(new LambdaQueryWrapper<ScheduleRepairSuggestion>()
                .eq(ScheduleRepairSuggestion::getRepairTaskId, task.getId())
                .eq(ScheduleRepairSuggestion::getStatus, V5SuggestionStatus.ACCEPTED.getCode())
                .orderByDesc(ScheduleRepairSuggestion::getUpdatedAt)
                .orderByDesc(ScheduleRepairSuggestion::getId)
                .last("LIMIT 1"));
        if (suggestion == null || suggestion.getSourcePlanItemId() == null) {
            return new ItemPair(null, null);
        }
        SchedulePlanItem before = planItemMapper.selectById(suggestion.getSourcePlanItemId());
        if (before == null) {
            return new ItemPair(null, null);
        }
        SuggestionMove move = readMove(suggestion);
        SchedulePlanItem after = planItemMapper.selectOne(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, simulation.getId())
                .eq(SchedulePlanItem::getTeachingTaskId, before.getTeachingTaskId())
                .eq(SchedulePlanItem::getWeekday, move.targetWeekday())
                .eq(SchedulePlanItem::getStartPeriod, move.targetStartPeriod())
                .eq(SchedulePlanItem::getEndPeriod, move.targetEndPeriod())
                .eq(SchedulePlanItem::getClassroomId, move.targetClassroomId())
                .last("LIMIT 1"));
        return new ItemPair(before, after);
    }

    private SchedulePlan resolveCompareBaseline(ScheduleRepairTask task, SchedulePlan simulation) {
        Long baselinePlanId = simulation.getSourcePlanId() != null
                ? simulation.getSourcePlanId()
                : (task.getPlanId() != null ? task.getPlanId() : task.getSourcePlanId());
        if (baselinePlanId == null) {
            return null;
        }
        SchedulePlan baseline = planMapper.selectById(baselinePlanId);
        if (baseline == null) {
            throw new BusinessException("试算来源方案不存在");
        }
        ensureComparableSemester(baseline, simulation);
        return baseline;
    }

    private void ensureComparableSemester(SchedulePlan baseline, SchedulePlan simulation) {
        if (baseline != null && !Objects.equals(baseline.getSemesterId(), simulation.getSemesterId())) {
            throw new BusinessException("原方案和试算方案不属于同一学期，不能对比");
        }
    }

    private List<SchedulePlanItemVo> loadCompareItems(SchedulePlan plan) {
        if (plan == null) {
            return List.of();
        }
        return schedulePlanService.getPlanItems(plan.getId());
    }

    private Map<Long, SchedulePlanItemVo> indexByTeachingTaskId(List<SchedulePlanItemVo> items) {
        return items.stream()
                .filter(item -> item.getTeachingTaskId() != null)
                .collect(Collectors.toMap(SchedulePlanItemVo::getTeachingTaskId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private List<V5SimulationItemChangeVo> buildChangedItems(
            Map<Long, SchedulePlanItemVo> baselineTaskMap,
            Map<Long, SchedulePlanItemVo> simulationTaskMap,
            SchedulePlanItem acceptedBefore,
            SchedulePlanItem acceptedAfter,
            Map<Long, String> classroomNames
    ) {
        Map<Long, V5SimulationItemChangeVo> changed = new LinkedHashMap<>();
        for (Map.Entry<Long, SchedulePlanItemVo> entry : simulationTaskMap.entrySet()) {
            SchedulePlanItemVo source = baselineTaskMap.get(entry.getKey());
            SchedulePlanItemVo target = entry.getValue();
            if (source != null && hasPlacementChanged(source, target)) {
                changed.put(entry.getKey(), buildItemChange(source, target, classroomNames));
            }
        }
        if (acceptedBefore != null && acceptedAfter != null && acceptedAfter.getTeachingTaskId() != null) {
            changed.putIfAbsent(acceptedAfter.getTeachingTaskId(),
                    buildItemChange(SchedulePlanItemVo.fromEntity(acceptedBefore),
                            SchedulePlanItemVo.fromEntity(acceptedAfter), classroomNames));
        }
        return new ArrayList<>(changed.values());
    }

    private boolean hasPlacementChanged(SchedulePlanItem before, SchedulePlanItem after) {
        return !Objects.equals(before.getWeekday(), after.getWeekday())
                || !Objects.equals(before.getStartPeriod(), after.getStartPeriod())
                || !Objects.equals(before.getEndPeriod(), after.getEndPeriod())
                || !Objects.equals(before.getClassroomId(), after.getClassroomId());
    }

    /** M-16：VO 重载，供 VO 链 compare 使用（baselineTaskMap/simulationTaskMap 条目）。 */
    private boolean hasPlacementChanged(SchedulePlanItemVo before, SchedulePlanItemVo after) {
        return !Objects.equals(before.getWeekday(), after.getWeekday())
                || !Objects.equals(before.getStartPeriod(), after.getStartPeriod())
                || !Objects.equals(before.getEndPeriod(), after.getEndPeriod())
                || !Objects.equals(before.getClassroomId(), after.getClassroomId());
    }

    private Set<Long> findChangedLockedItemIds(
            SchedulePlan baseline,
            Map<Long, SchedulePlanItemVo> baselineTaskMap,
            Map<Long, SchedulePlanItemVo> simulationTaskMap
    ) {
        if (baseline == null) {
            return Set.of();
        }
        List<ScheduleLockedItem> lockedItems = lockedItemMapper.selectList(new LambdaQueryWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getPlanId, baseline.getId())
                .eq(ScheduleLockedItem::getActiveFlag, 1));
        if (lockedItems.isEmpty()) {
            return Set.of();
        }
        Set<Long> changed = new LinkedHashSet<>();
        for (ScheduleLockedItem lockedItem : lockedItems) {
            if (lockedItem.getPlanItemId() == null) {
                continue;
            }
            SchedulePlanItem baselineItem = planItemMapper.selectById(lockedItem.getPlanItemId());
            if (baselineItem == null || baselineItem.getTeachingTaskId() == null) {
                continue;
            }
            SchedulePlanItemVo source = baselineTaskMap.get(baselineItem.getTeachingTaskId());
            SchedulePlanItemVo target = simulationTaskMap.get(baselineItem.getTeachingTaskId());
            if (source != null && target != null && hasPlacementChanged(source, target)) {
                changed.add(baselineItem.getTeachingTaskId());
            }
        }
        return changed;
    }

    private List<String> resolveLockedCourseNames(Map<Long, SchedulePlanItemVo> baselineTaskMap, Set<Long> changedLockedItemIds) {
        List<String> names = new ArrayList<>();
        for (Long taskId : changedLockedItemIds) {
            SchedulePlanItemVo item = baselineTaskMap.get(taskId);
            if (item != null) {
                names.add(firstNonBlank(item.getCourseName(), "教学任务#" + taskId));
            }
        }
        return names;
    }

    private List<ScheduleRiskIssueVo> diffRisks(ScheduleRiskListVo source, ScheduleRiskListVo target) {
        List<ScheduleRiskIssueVo> sourceRisks = source == null || source.getRisks() == null ? List.of() : source.getRisks();
        Set<String> targetKeys = (target == null || target.getRisks() == null ? List.<ScheduleRiskIssueVo>of() : target.getRisks()).stream()
                .map(this::riskKey)
                .collect(Collectors.toSet());
        return sourceRisks.stream()
                .filter(risk -> !targetKeys.contains(riskKey(risk)))
                .toList();
    }

    private String riskKey(ScheduleRiskIssueVo risk) {
        return String.join("|",
                firstNonBlank(risk.getRiskType(), ""),
                firstNonBlank(risk.getLevel(), ""),
                firstNonBlank(risk.getTitle(), ""),
                firstNonBlank(risk.getDescription(), ""),
                String.valueOf(risk.getWeekDay()),
                firstNonBlank(risk.getPeriod(), ""),
                String.valueOf(risk.getRelatedTeacherId()),
                String.valueOf(risk.getRelatedClassId()),
                String.valueOf(risk.getRelatedRoomId()),
                String.valueOf(risk.getRelatedCourseId()));
    }

    private List<V5SimulationLoadChangeVo> buildLoadChanges(
            List<SchedulePlanItemVo> baselineItems,
            List<SchedulePlanItemVo> simulationItems,
            Function<SchedulePlanItemVo, Long> idGetter,
            Function<SchedulePlanItemVo, String> nameGetter
    ) {
        Map<Long, Integer> baselineLoads = aggregateLoad(baselineItems, idGetter);
        Map<Long, Integer> simulationLoads = aggregateLoad(simulationItems, idGetter);
        Map<Long, String> names = new HashMap<>();
        baselineItems.forEach(item -> putName(names, idGetter.apply(item), nameGetter.apply(item)));
        simulationItems.forEach(item -> putName(names, idGetter.apply(item), nameGetter.apply(item)));
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(baselineLoads.keySet());
        ids.addAll(simulationLoads.keySet());
        List<V5SimulationLoadChangeVo> changes = new ArrayList<>();
        for (Long id : ids) {
            int baselineLoad = baselineLoads.getOrDefault(id, 0);
            int simulationLoad = simulationLoads.getOrDefault(id, 0);
            if (baselineLoad == simulationLoad) {
                continue;
            }
            V5SimulationLoadChangeVo vo = new V5SimulationLoadChangeVo();
            vo.setEntityId(id);
            vo.setEntityName(firstNonBlank(names.get(id), id == null ? "未命名" : String.valueOf(id)));
            vo.setBaselineLoad(baselineLoad);
            vo.setSimulationLoad(simulationLoad);
            vo.setDelta(simulationLoad - baselineLoad);
            changes.add(vo);
        }
        changes.sort(Comparator.comparing((V5SimulationLoadChangeVo vo) -> Math.abs(vo.getDelta())).reversed()
                .thenComparing(V5SimulationLoadChangeVo::getEntityName, Comparator.nullsLast(String::compareTo)));
        return changes;
    }

    private Map<Long, Integer> aggregateLoad(List<SchedulePlanItemVo> items, Function<SchedulePlanItemVo, Long> idGetter) {
        Map<Long, Integer> loads = new LinkedHashMap<>();
        for (SchedulePlanItemVo item : items) {
            Long id = idGetter.apply(item);
            if (id == null) {
                continue;
            }
            loads.merge(id, periodSpan(item), V5SimulationService::sumIntegers);
        }
        return loads;
    }

    private static Integer sumIntegers(Integer left, Integer right) {
        int safeLeft = left == null ? 0 : left;
        int safeRight = right == null ? 0 : right;
        return safeLeft + safeRight;
    }

    private List<V5SimulationRoomUtilizationChangeVo> buildRoomUtilizationChanges(
            List<SchedulePlanItemVo> baselineItems,
            List<SchedulePlanItemVo> simulationItems,
            Map<Long, String> classroomNames
    ) {
        int totalPeriods = Math.max(1, timeSlotMapper.selectList(null).size() * 2);
        Map<Long, Integer> baselineLoads = aggregateLoad(baselineItems, SchedulePlanItemVo::getClassroomId);
        Map<Long, Integer> simulationLoads = aggregateLoad(simulationItems, SchedulePlanItemVo::getClassroomId);
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(baselineLoads.keySet());
        ids.addAll(simulationLoads.keySet());
        List<V5SimulationRoomUtilizationChangeVo> changes = new ArrayList<>();
        for (Long roomId : ids) {
            int baselineLoad = baselineLoads.getOrDefault(roomId, 0);
            int simulationLoad = simulationLoads.getOrDefault(roomId, 0);
            if (baselineLoad == simulationLoad) {
                continue;
            }
            V5SimulationRoomUtilizationChangeVo vo = new V5SimulationRoomUtilizationChangeVo();
            vo.setClassroomId(roomId);
            vo.setClassroomName(classroomName(roomId, classroomNames));
            vo.setBaselineUsedPeriods(baselineLoad);
            vo.setSimulationUsedPeriods(simulationLoad);
            vo.setDeltaPeriods(simulationLoad - baselineLoad);
            vo.setBaselineUtilizationRate(rate(baselineLoad, totalPeriods));
            vo.setSimulationUtilizationRate(rate(simulationLoad, totalPeriods));
            vo.setUtilizationDelta(vo.getSimulationUtilizationRate().subtract(vo.getBaselineUtilizationRate()));
            changes.add(vo);
        }
        changes.sort(Comparator.comparing((V5SimulationRoomUtilizationChangeVo vo) -> vo.getUtilizationDelta().abs()).reversed()
                .thenComparing(V5SimulationRoomUtilizationChangeVo::getClassroomName, Comparator.nullsLast(String::compareTo)));
        return changes;
    }

    private BigDecimal rate(int numerator, int denominator) {
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(Math.max(1, denominator)), 2, RoundingMode.HALF_UP);
    }

    private int periodSpan(SchedulePlanItemVo item) {
        int start = item.getStartPeriod() == null ? 0 : item.getStartPeriod();
        int end = item.getEndPeriod() == null ? start : item.getEndPeriod();
        return Math.max(0, end - start + 1);
    }

    private void putName(Map<Long, String> names, Long id, String name) {
        if (id != null && name != null && !name.isBlank()) {
            names.putIfAbsent(id, name);
        }
    }

    private String buildSummary(V5SimulationCompareVo vo) {
        List<String> parts = new ArrayList<>();
        parts.add("评分" + signed(vo.getScoreDelta()));
        parts.add("已排任务" + signed(vo.getScheduledDelta()));
        parts.add("未排任务" + signed(vo.getUnscheduledDelta()));
        parts.add("高风险" + signed(vo.getHighRiskDelta()));
        parts.add("中风险" + signed(vo.getMediumRiskDelta()));
        parts.add("低风险" + signed(vo.getLowRiskDelta()));
        if (Boolean.TRUE.equals(vo.getHasNewHardConflicts())) {
            parts.add("新增硬冲突" + vo.getNewHardConflictCount());
        }
        if (!Boolean.TRUE.equals(vo.getLockedCoursesPreserved())) {
            parts.add("锁定课程发生变动");
        }
        return String.join("，", parts);
    }

    private String buildRecommendationMessage(V5SimulationCompareVo vo) {
        if (Boolean.TRUE.equals(vo.getHasNewHardConflicts())) {
            return "试算方案引入新的硬冲突，不推荐应用";
        }
        if (!Boolean.TRUE.equals(vo.getLockedCoursesPreserved())) {
            return "试算方案改动了锁定课程，建议人工复核后再应用";
        }
        return "试算方案未引入新的硬冲突，可继续确认或应用";
    }

    private void persistCompare(ScheduleRepairTask task, SchedulePlan baseline, SchedulePlan simulation, V5SimulationCompareVo vo) {
        ScheduleOptimizationCompare compare = new ScheduleOptimizationCompare();
        compare.setSemesterId(simulation.getSemesterId());
        compare.setRepairTaskId(task.getId());
        compare.setBaselinePlanId(baseline == null ? null : baseline.getId());
        compare.setOptimizedPlanId(simulation.getId());
        compare.setBaselineTotalScore(vo.getBaselineScore());
        compare.setOptimizedTotalScore(vo.getSimulationScore());
        compare.setScoreDelta(vo.getScoreDelta());
        compare.setBaselineRiskCount(vo.getBaselineRiskCount());
        compare.setOptimizedRiskCount(vo.getSimulationRiskCount());
        compare.setRiskDelta(vo.getRiskDelta());
        compare.setBaselineUnscheduledCount(baseline == null || baseline.getUnscheduledCount() == null ? 0 : baseline.getUnscheduledCount());
        compare.setOptimizedUnscheduledCount(simulation.getUnscheduledCount() == null ? 0 : simulation.getUnscheduledCount());
        compare.setUnscheduledDelta(compare.getOptimizedUnscheduledCount() - compare.getBaselineUnscheduledCount());
        compare.setBaselineConflictCount(vo.getBaselineConflictCount());
        compare.setOptimizedConflictCount(vo.getSimulationConflictCount());
        compare.setConflictDelta(vo.getConflictDelta());
        compare.setCompareSummary(vo.getSummary());
        compare.setCreatedAt(LocalDateTime.now());
        compare.setUpdatedAt(LocalDateTime.now());
        compareMapper.insert(compare);
    }

    private SchedulePlan requireSimulationPlan(ScheduleRepairTask task, Long planId) {
        SchedulePlan plan = planMapper.selectById(planId);
        if (plan == null) throw new BusinessException("试算方案不存在");
        if (!Objects.equals(plan.getRepairTaskId(), task.getId())) {
            throw new BusinessException("试算方案不属于当前修复任务");
        }
        if (!List.of(SchedulePlanStatus.SIMULATION.getCode(), SchedulePlanStatus.CONFIRMED.getCode(), SchedulePlanStatus.DISCARDED.getCode(), SchedulePlanStatus.APPLIED.getCode()).contains(plan.getStatus())) {
            throw new BusinessException("目标方案不是试算方案");
        }
        return plan;
    }

    private ScheduleRepairTask requireTask(Long taskId) {
        ScheduleRepairTask task = repairTaskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("修复任务不存在");
        return task;
    }

    private ScheduleRepairSuggestion requireSuggestion(Long taskId, Long suggestionId) {
        ScheduleRepairSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null || !Objects.equals(suggestion.getRepairTaskId(), taskId)) {
            throw new BusinessException("修复建议不存在");
        }
        return suggestion;
    }

    private SchedulePlanItem copyDetachedItem(SchedulePlanItem item) {
        SchedulePlanItem copy = new SchedulePlanItem();
        copy.setId(item.getId());
        copy.setPlanId(item.getPlanId());
        copy.setSemesterId(item.getSemesterId());
        copy.setTeachingTaskId(item.getTeachingTaskId());
        copy.setTeacherId(item.getTeacherId());
        copy.setClassId(item.getClassId());
        copy.setCourseId(item.getCourseId());
        copy.setClassroomId(item.getClassroomId());
        copy.setWeekday(item.getWeekday());
        copy.setStartPeriod(item.getStartPeriod());
        copy.setEndPeriod(item.getEndPeriod());
        copy.setWeekType(item.getWeekType());
        copy.setScore(item.getScore());
        copy.setConflictFlag(item.getConflictFlag());
        copy.setConflictReason(item.getConflictReason());
        copy.setSourceType(item.getSourceType());
        copy.setCreatedAt(item.getCreatedAt());
        copy.setUpdatedAt(item.getUpdatedAt());
        return copy;
    }

    private SuggestionMove readMove(ScheduleRepairSuggestion suggestion) {
        try {
            JsonNode node = objectMapper.readTree(suggestion.getDetailJson());
            return new SuggestionMove(
                    longValue(node, "targetClassroomId"),
                    intValue(node, "targetWeekday"),
                    intValue(node, "targetStartPeriod"),
                    intValue(node, "targetEndPeriod")
            );
        } catch (Exception e) {
            return new SuggestionMove(null, null, null, null);
        }
    }

    private Map<Long, String> loadClassroomNames(
            List<SchedulePlanItemVo> baselineItems,
            List<SchedulePlanItemVo> simulationItems,
            SchedulePlanItem before,
            SchedulePlanItem after
    ) {
        Set<Long> ids = new LinkedHashSet<>();
        addClassroomIds(ids, baselineItems);
        addClassroomIds(ids, simulationItems);
        if (before != null && before.getClassroomId() != null) {
            ids.add(before.getClassroomId());
        }
        if (after != null && after.getClassroomId() != null) {
            ids.add(after.getClassroomId());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return classroomMapper.selectBatchIds(ids).stream()
                .filter(classroom -> classroom.getId() != null)
                .collect(Collectors.toMap(Classroom::getId, Classroom::getRoomName, (a, b) -> a));
    }

    private void addClassroomIds(Set<Long> ids, List<SchedulePlanItemVo> items) {
        for (SchedulePlanItemVo item : items) {
            if (item.getClassroomId() != null) {
                ids.add(item.getClassroomId());
            }
        }
    }

    private String classroomName(Long classroomId, Map<Long, String> classroomNames) {
        if (classroomId == null) return null;
        return classroomNames.get(classroomId);
    }

    private BigDecimal scoreOf(SchedulePlan plan) {
        return plan == null || plan.getTotalScore() == null ? BigDecimal.ZERO : plan.getTotalScore();
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private int riskLevelCount(Integer value) {
        return value == null ? 0 : value;
    }

    private int countRisks(ScheduleRiskListVo risks) {
        return risks == null || risks.getRiskCount() == null ? 0 : risks.getRiskCount();
    }

    private String signed(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.signum() > 0 ? "+" + value.toPlainString() : value.toPlainString();
    }

    private String signed(Integer value) {
        if (value == null) {
            return "0";
        }
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private ScheduleRiskListVo emptyRisks(Long planId) {
        ScheduleRiskListVo vo = new ScheduleRiskListVo();
        vo.setPlanId(planId);
        vo.setRiskCount(0);
        vo.setHighRiskCount(0);
        vo.setMediumRiskCount(0);
        vo.setLowRiskCount(0);
        vo.setUnresolvedCount(0);
        vo.setRisks(List.of());
        return vo;
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private Integer intValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private boolean isTerminal(String status) {
        return V5RepairTaskStatus.CANCELLED.getCode().equals(status)
                || V5RepairTaskStatus.FAILED.getCode().equals(status)
                || V5RepairTaskStatus.APPLIED.getCode().equals(status);
    }

    private <T> T runInTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private record SuggestionMove(Long targetClassroomId, Integer targetWeekday, Integer targetStartPeriod, Integer targetEndPeriod) {
        private boolean executable() {
            return targetClassroomId != null && targetWeekday != null && targetStartPeriod != null && targetEndPeriod != null;
        }
    }

    private record LocalReplanResult(Long planId, V5LocalReplanSummaryVo summary) {
    }

    private record CandidatePlacement(Long classroomId, Integer weekday, Integer startPeriod, Integer endPeriod, BigDecimal score) {
    }

    private record ItemPair(SchedulePlanItem before, SchedulePlanItem after) {
    }
}
