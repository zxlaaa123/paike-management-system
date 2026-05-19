package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ScheduleLockedItem;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleUnassignedTask;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleUnassignedTaskMapper;
import com.paike.scheduler.service.dto.V4ScheduleReplanRequest;
import com.paike.scheduler.service.vo.ScheduleReplanResultVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class V4ScheduleReplanService {

    private static final String DEFAULT_STRATEGY_CODE = "LOCAL_REPLAN";
    private static final DateTimeFormatter PLAN_NAME_SUFFIX = DateTimeFormatter.ofPattern("MMddHHmmss", Locale.ROOT);

    private final SchedulePlanMapper schedulePlanMapper;
    private final SchedulePlanItemMapper schedulePlanItemMapper;
    private final ScheduleLockedItemMapper scheduleLockedItemMapper;
    private final ScheduleUnassignedTaskMapper scheduleUnassignedTaskMapper;
    private final SchedulePlanService schedulePlanService;
    private final ScheduleScoreService scheduleScoreService;
    private final SchedulePlanExplainService schedulePlanExplainService;

    @Transactional(rollbackFor = Exception.class)
    public ScheduleReplanResultVo createLocalReplanPlan(Long sourcePlanId, V4ScheduleReplanRequest request) {
        SchedulePlan sourcePlan = schedulePlanMapper.selectById(sourcePlanId);
        if (sourcePlan == null) {
            throw new BusinessException("来源排课方案不存在");
        }
        if ("ABANDONED".equals(sourcePlan.getStatus())) {
            throw new BusinessException("已废弃方案不能进行局部重排");
        }
        if ("FAILED".equals(sourcePlan.getStatus())) {
            throw new BusinessException("生成失败方案不能进行局部重排");
        }

        List<SchedulePlanItem> sourceItems = schedulePlanItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, sourcePlanId)
                        .orderByAsc(SchedulePlanItem::getWeekday)
                        .orderByAsc(SchedulePlanItem::getStartPeriod)
                        .orderByAsc(SchedulePlanItem::getId));
        if (sourceItems.isEmpty()) {
            throw new BusinessException("该方案没有排课明细，无法生成局部重排方案");
        }

        V4ScheduleReplanRequest safeRequest = request == null ? new V4ScheduleReplanRequest() : request;
        boolean keepLocked = !Boolean.FALSE.equals(safeRequest.getKeepLocked());
        if (Boolean.FALSE.equals(safeRequest.getKeepLocked())) {
            throw new BusinessException("V5 修复约束：锁定课程不可移动，局部重排必须保留锁定项");
        }
        String strategyCode = normalizeStrategyCode(safeRequest.getStrategyCode());
        String newPlanName = resolvePlanName(safeRequest.getNewPlanName(), sourcePlan.getName());

        List<ScheduleLockedItem> activeLocks = loadActivePlanLocks(sourcePlanId);
        Set<Long> lockedPlanItemIds = activeLocks.stream()
                .map(ScheduleLockedItem::getPlanItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        SchedulePlan newPlan = buildNewPlan(sourcePlan, newPlanName, strategyCode, keepLocked);
        schedulePlanMapper.insert(newPlan);
        schedulePlanExplainService.clearPlanArtifacts(newPlan.getId());

        Map<Long, Long> itemIdMapping = copyPlanItems(sourceItems, newPlan);
        copyGenerateLogs(sourcePlan, newPlan, lockedPlanItemIds.size(), Math.max(0, sourceItems.size() - lockedPlanItemIds.size()));
        copyUnassignedTasks(sourcePlanId, newPlan.getId(), newPlan.getSemesterId());
        if (keepLocked) {
            copyLockedItems(activeLocks, itemIdMapping, newPlan.getId());
        }

        schedulePlanService.refreshPlanConflictState(newPlan.getId());
        SchedulePlan refreshedPlan = schedulePlanMapper.selectById(newPlan.getId());
        scheduleScoreService.rescore(refreshedPlan);
        SchedulePlan scoredPlan = schedulePlanMapper.selectById(newPlan.getId());

        ScheduleReplanResultVo result = new ScheduleReplanResultVo();
        result.setSourcePlanId(sourcePlan.getId());
        result.setSourcePlanName(sourcePlan.getName());
        result.setNewPlanId(scoredPlan.getId());
        result.setNewPlanName(scoredPlan.getName());
        result.setLockedCount(lockedPlanItemIds.size());
        result.setReplanableCount(Math.max(0, sourceItems.size() - lockedPlanItemIds.size()));
        result.setScheduledCount(scoredPlan.getScheduledCount());
        result.setUnscheduledCount(scoredPlan.getUnscheduledCount());
        result.setConflictCount(scoredPlan.getConflictCount());
        result.setTotalScore(normalizeScore(scoredPlan.getTotalScore()));
        result.setKeepLocked(keepLocked);
        result.setStrategyCode(strategyCode);
        result.setMinimalMode(true);
        result.setMessage("局部重排方案已生成");
        return result;
    }

    private SchedulePlan buildNewPlan(SchedulePlan sourcePlan, String newPlanName, String strategyCode, boolean keepLocked) {
        LocalDateTime now = LocalDateTime.now();
        SchedulePlan newPlan = new SchedulePlan();
        newPlan.setSemesterId(sourcePlan.getSemesterId());
        newPlan.setName(newPlanName);
        newPlan.setStrategyType(sourcePlan.getStrategyType());
        newPlan.setStatus("DRAFT");
        newPlan.setScheduledCount(sourcePlan.getScheduledCount());
        newPlan.setUnscheduledCount(sourcePlan.getUnscheduledCount());
        newPlan.setConflictCount(sourcePlan.getConflictCount());
        newPlan.setDescription(buildDescription(sourcePlan, strategyCode, keepLocked));
        newPlan.setGeneratedBy("V4_LOCAL_REPLAN");
        newPlan.setGeneratedAt(now);
        newPlan.setCreatedAt(now);
        newPlan.setUpdatedAt(now);
        return newPlan;
    }

    private Map<Long, Long> copyPlanItems(List<SchedulePlanItem> sourceItems, SchedulePlan newPlan) {
        Map<Long, Long> itemIdMapping = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (SchedulePlanItem sourceItem : sourceItems) {
            SchedulePlanItem target = new SchedulePlanItem();
            target.setPlanId(newPlan.getId());
            target.setSemesterId(newPlan.getSemesterId());
            target.setTeachingTaskId(sourceItem.getTeachingTaskId());
            target.setTeacherId(sourceItem.getTeacherId());
            target.setClassId(sourceItem.getClassId());
            target.setCourseId(sourceItem.getCourseId());
            target.setClassroomId(sourceItem.getClassroomId());
            target.setWeekday(sourceItem.getWeekday());
            target.setStartPeriod(sourceItem.getStartPeriod());
            target.setEndPeriod(sourceItem.getEndPeriod());
            target.setWeekType(sourceItem.getWeekType());
            target.setScore(sourceItem.getScore());
            target.setConflictFlag(sourceItem.getConflictFlag());
            target.setConflictReason(sourceItem.getConflictReason());
            target.setSourceType(sourceItem.getSourceType());
            target.setCreatedAt(now);
            target.setUpdatedAt(now);
            schedulePlanItemMapper.insert(target);
            itemIdMapping.put(sourceItem.getId(), target.getId());
        }
        return itemIdMapping;
    }

    private void copyGenerateLogs(SchedulePlan sourcePlan, SchedulePlan newPlan, int lockedCount, int replanableCount) {
        schedulePlanExplainService.appendGenerateLog(
                newPlan.getId(),
                newPlan.getSemesterId(),
                null,
                "INFO",
                DEFAULT_STRATEGY_CODE,
                "局部重排开始：基于方案「" + sourcePlan.getName() + "」生成新方案，不覆盖原方案。",
                1
        );
        schedulePlanExplainService.appendGenerateLog(
                newPlan.getId(),
                newPlan.getSemesterId(),
                null,
                "INFO",
                DEFAULT_STRATEGY_CODE,
                "当前为最小可用版：复制原方案明细，保留 " + lockedCount + " 条锁定课程，未锁定课程暂按原安排生成新方案。",
                2
        );
        schedulePlanExplainService.appendGenerateLog(
                newPlan.getId(),
                newPlan.getSemesterId(),
                null,
                "INFO",
                DEFAULT_STRATEGY_CODE,
                "局部重排完成：可重排课程 " + replanableCount + " 条。新方案仍需走 V3 apply 才能成为正式课表。",
                3
        );
    }

    private void copyUnassignedTasks(Long sourcePlanId, Long newPlanId, Long semesterId) {
        List<ScheduleUnassignedTask> sourceTasks = scheduleUnassignedTaskMapper.selectList(
                new LambdaQueryWrapper<ScheduleUnassignedTask>()
                        .eq(ScheduleUnassignedTask::getPlanId, sourcePlanId)
                        .orderByAsc(ScheduleUnassignedTask::getId));
        LocalDateTime now = LocalDateTime.now();
        for (ScheduleUnassignedTask sourceTask : sourceTasks) {
            ScheduleUnassignedTask target = new ScheduleUnassignedTask();
            target.setPlanId(newPlanId);
            target.setSemesterId(semesterId);
            target.setTeachingTaskId(sourceTask.getTeachingTaskId());
            target.setReasonCode(sourceTask.getReasonCode());
            target.setReasonMessage(sourceTask.getReasonMessage());
            target.setSuggestion(sourceTask.getSuggestion());
            target.setCreatedAt(now);
            scheduleUnassignedTaskMapper.insert(target);
        }
    }

    private void copyLockedItems(List<ScheduleLockedItem> activeLocks, Map<Long, Long> itemIdMapping, Long newPlanId) {
        for (ScheduleLockedItem sourceLock : activeLocks) {
            Long mappedPlanItemId = itemIdMapping.get(sourceLock.getPlanItemId());
            if (mappedPlanItemId == null) {
                continue;
            }
            ScheduleLockedItem target = new ScheduleLockedItem();
            target.setTargetType("PLAN");
            target.setPlanId(newPlanId);
            target.setPlanItemId(mappedPlanItemId);
            target.setScheduleId(null);
            target.setLockReason(sourceLock.getLockReason());
            target.setActiveFlag(1);
            scheduleLockedItemMapper.insert(target);
        }
    }

    private List<ScheduleLockedItem> loadActivePlanLocks(Long planId) {
        return scheduleLockedItemMapper.selectList(
                new LambdaQueryWrapper<ScheduleLockedItem>()
                        .eq(ScheduleLockedItem::getPlanId, planId)
                        .eq(ScheduleLockedItem::getActiveFlag, 1)
                        .isNotNull(ScheduleLockedItem::getPlanItemId)
                        .orderByAsc(ScheduleLockedItem::getId));
    }

    private String resolvePlanName(String requestedName, String sourcePlanName) {
        String trimmed = trimToNull(requestedName);
        if (trimmed != null) {
            return trimmed;
        }
        return sourcePlanName + "-局部重排版-" + LocalDateTime.now().format(PLAN_NAME_SUFFIX);
    }

    private String normalizeStrategyCode(String strategyCode) {
        String trimmed = trimToNull(strategyCode);
        return trimmed == null ? DEFAULT_STRATEGY_CODE : trimmed;
    }

    private String buildDescription(SchedulePlan sourcePlan, String strategyCode, boolean keepLocked) {
        return "V4 阶段 8 局部重排生成方案；来源方案 ID=" + sourcePlan.getId()
                + "；策略=" + strategyCode
                + "；保留锁定课程=" + (keepLocked ? "是" : "否")
                + "；当前为最小可用版，不直接修改正式课表。";
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        return score == null ? null : score.stripTrailingZeros();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
