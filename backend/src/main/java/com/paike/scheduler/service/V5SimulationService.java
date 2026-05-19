package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.V5RepairTaskStatus;
import com.paike.scheduler.common.enums.V5SuggestionStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleOptimizationCompare;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRepairSuggestion;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.ScheduleOptimizationCompareMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRepairSuggestionMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.mapper.ScheduleScoreDetailMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import com.paike.scheduler.service.vo.V5SimulationCompareVo;
import com.paike.scheduler.service.vo.V5SimulationItemChangeVo;
import com.paike.scheduler.service.vo.V5SimulationPlanDetailVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final ScheduleOptimizationCompareMapper compareMapper;
    private final SchedulePlanService schedulePlanService;
    private final ScheduleScoreService scoreService;
    private final V4ScheduleRiskService riskService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public V5SimulationPlanDetailVo generate(Long taskId, Long suggestionId) {
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

        return detail(taskId, simulation.getId());
    }

    public V5SimulationPlanDetailVo detail(Long taskId, Long planId) {
        ScheduleRepairTask task = requireTask(taskId);
        SchedulePlan plan = requireSimulationPlan(task, planId);
        SchedulePlan baseline = plan.getSourcePlanId() == null ? null : planMapper.selectById(plan.getSourcePlanId());
        ScheduleRiskListVo risks = riskService.getPlanRisks(plan.getId(), null, null, null);
        ScheduleRiskListVo baselineRisks = baseline == null ? emptyRisks(null) : riskService.getPlanRisks(baseline.getId(), null, null, null);
        ItemPair changedItem = resolveAcceptedSuggestionChange(task, plan);

        V5SimulationPlanDetailVo vo = new V5SimulationPlanDetailVo();
        vo.setPlan(plan);
        vo.setItems(schedulePlanService.getPlanItems(plan.getId()));
        vo.setScoreDetails(scoreDetailMapper.selectList(new LambdaQueryWrapper<ScheduleScoreDetail>()
                .eq(ScheduleScoreDetail::getPlanId, plan.getId())
                .orderByAsc(ScheduleScoreDetail::getRuleCode)));
        vo.setRisks(risks);
        vo.setCompare(buildCompare(baseline, plan, baselineRisks, risks, changedItem.before(), changedItem.after()));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public V5SimulationPlanDetailVo confirm(Long taskId, Long planId) {
        ScheduleRepairTask task = requireTask(taskId);
        SchedulePlan plan = requireSimulationPlan(task, planId);
        if (!"SIMULATION".equals(plan.getStatus())) {
            throw new BusinessException("只有试算方案可以确认");
        }
        plan.setStatus("CONFIRMED");
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return detail(taskId, planId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(Long taskId, Long planId) {
        ScheduleRepairTask task = requireTask(taskId);
        SchedulePlan plan = requireSimulationPlan(task, planId);
        if (!"SIMULATION".equals(plan.getStatus()) && !"CONFIRMED".equals(plan.getStatus())) {
            throw new BusinessException("只有试算或已确认方案可以应用");
        }
        schedulePlanService.refreshPlanConflictState(planId);
        scoreService.rescore(planMapper.selectById(planId));
        plan = planMapper.selectById(planId);
        if (plan.getConflictCount() != null && plan.getConflictCount() > 0) {
            throw new BusinessException("试算方案仍存在冲突，不能应用");
        }
        if ("SIMULATION".equals(plan.getStatus())) {
            plan.setStatus("CONFIRMED");
            plan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(plan);
        }
        Map<String, Object> result = schedulePlanService.applySimulationPlan(planId);
        task.setStatus(V5RepairTaskStatus.APPLIED.getCode());
        task.setResultPlanId(planId);
        task.setFinishedAt(LocalDateTime.now());
        repairTaskMapper.updateById(task);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public V5SimulationPlanDetailVo discard(Long taskId, Long planId) {
        ScheduleRepairTask task = requireTask(taskId);
        SchedulePlan plan = requireSimulationPlan(task, planId);
        if ("APPLIED".equals(plan.getStatus())) {
            throw new BusinessException("已应用试算方案不能放弃");
        }
        plan.setStatus("DISCARDED");
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        if (Objects.equals(task.getResultPlanId(), planId)) {
            task.setStatus(V5RepairTaskStatus.SUGGESTED.getCode());
            task.setResultPlanId(null);
            task.setUpdatedAt(LocalDateTime.now());
            repairTaskMapper.updateById(task);
        }
        return detail(taskId, planId);
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
        plan.setStatus("SIMULATION");
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
                .eq(Schedule::getSemesterId, task.getSemesterId())
                .eq(Schedule::getDeleted, 0));
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
        if ("SIMULATION".equals(plan.getStatus()) || "DISCARDED".equals(plan.getStatus())) {
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
        V5SimulationCompareVo vo = new V5SimulationCompareVo();
        vo.setBaselinePlanId(baseline == null ? null : baseline.getId());
        vo.setSimulationPlanId(simulation.getId());
        vo.setBaselineScore(scoreOf(baseline));
        vo.setSimulationScore(scoreOf(simulation));
        vo.setScoreDelta(vo.getSimulationScore().subtract(vo.getBaselineScore()));
        vo.setBaselineRiskCount(countRisks(baselineRisks));
        vo.setSimulationRiskCount(countRisks(simulationRisks));
        vo.setRiskDelta(vo.getSimulationRiskCount() - vo.getBaselineRiskCount());
        vo.setBaselineConflictCount(baseline == null || baseline.getConflictCount() == null ? 0 : baseline.getConflictCount());
        vo.setSimulationConflictCount(simulation.getConflictCount() == null ? 0 : simulation.getConflictCount());
        vo.setConflictDelta(vo.getSimulationConflictCount() - vo.getBaselineConflictCount());
        vo.setChangedItems(before != null && after != null ? List.of(buildItemChange(before, after)) : List.of());
        vo.setSummary("评分变化 " + vo.getScoreDelta() + "，风险变化 " + vo.getRiskDelta() + "，冲突变化 " + vo.getConflictDelta());
        return vo;
    }

    private V5SimulationItemChangeVo buildItemChange(SchedulePlanItem before, SchedulePlanItem after) {
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
        vo.setBeforeClassroomName(classroomName(before.getClassroomId()));
        vo.setAfterWeekday(after.getWeekday());
        vo.setAfterStartPeriod(after.getStartPeriod());
        vo.setAfterEndPeriod(after.getEndPeriod());
        vo.setAfterClassroomId(after.getClassroomId());
        vo.setAfterClassroomName(classroomName(after.getClassroomId()));
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
        if (!List.of("SIMULATION", "CONFIRMED", "DISCARDED", "APPLIED").contains(plan.getStatus())) {
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

    private String classroomName(Long classroomId) {
        if (classroomId == null) return null;
        Classroom classroom = classroomMapper.selectById(classroomId);
        return classroom == null ? null : classroom.getRoomName();
    }

    private BigDecimal scoreOf(SchedulePlan plan) {
        return plan == null || plan.getTotalScore() == null ? BigDecimal.ZERO : plan.getTotalScore();
    }

    private int countRisks(ScheduleRiskListVo risks) {
        return risks == null || risks.getRiskCount() == null ? 0 : risks.getRiskCount();
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

    private record SuggestionMove(Long targetClassroomId, Integer targetWeekday, Integer targetStartPeriod, Integer targetEndPeriod) {
        private boolean executable() {
            return targetClassroomId != null && targetWeekday != null && targetStartPeriod != null && targetEndPeriod != null;
        }
    }

    private record ItemPair(SchedulePlanItem before, SchedulePlanItem after) {
    }
}
