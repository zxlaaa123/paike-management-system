package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.V5SuggestionStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRepairSuggestion;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.ScheduleRepairSuggestionMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.service.dto.V5CandidatePositionGenerateRequest;
import com.paike.scheduler.service.dto.V5RepairSuggestionGenerateRequest;
import com.paike.scheduler.service.vo.ScheduleRiskIssueVo;
import com.paike.scheduler.service.vo.ScheduleRiskListVo;
import com.paike.scheduler.service.vo.V5CandidatePositionResultVo;
import com.paike.scheduler.service.vo.V5CandidatePositionVo;
import com.paike.scheduler.service.vo.V5RepairSuggestionVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class V5RepairSuggestionService {

    private final ScheduleRepairTaskMapper repairTaskMapper;
    private final ScheduleRepairSuggestionMapper suggestionMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final ClassroomMapper classroomMapper;
    private final V5CandidatePositionService candidatePositionService;
    private final V4ScheduleRiskService riskService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public List<V5RepairSuggestionVo> generate(Long taskId, V5RepairSuggestionGenerateRequest request) {
        ScheduleRepairTask task = requireTask(taskId);
        if ("CANCELLED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            throw new BusinessException("已取消或失败任务不能生成建议");
        }
        if (task.getPlanId() == null) {
            throw new BusinessException("修复任务缺少 planId，无法生成建议");
        }

        clearTaskSuggestions(taskId);
        List<Long> scopeIds = readLongList(task.getScopePlanItemIds());
        List<Long> riskIds = readLongList(task.getRiskItemIds());
        Map<Long, ScheduleRiskIssueVo> riskMap = loadRiskMap(task.getPlanId(), riskIds);

        List<Long> targetIds = new ArrayList<>(scopeIds);
        if (targetIds.isEmpty()) {
            targetIds.addAll(resolveRelatedItemIds(riskMap.values()));
        }
        targetIds = targetIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targetIds.isEmpty()) {
            ScheduleRepairSuggestion manual = manualSuggestion(task, null, null, "未定位到可修复课程，建议人工处理");
            suggestionMapper.insert(manual);
            updateTaskSuggested(task);
            return listByTask(taskId);
        }

        int candidateLimit = request != null && request.getCandidateLimit() != null ? request.getCandidateLimit() : 24;
        boolean includeUnavailable = request != null && Boolean.TRUE.equals(request.getIncludeUnavailable());
        List<ScheduleRepairSuggestion> inserts = new ArrayList<>();
        for (Long itemId : targetIds) {
            SchedulePlanItem source = planItemMapper.selectById(itemId);
            if (source == null || !Objects.equals(source.getPlanId(), task.getPlanId())) continue;
            ScheduleRiskIssueVo risk = matchRiskForItem(riskMap.values(), itemId);
            V5CandidatePositionResultVo candidates = candidatePositionService.generate(buildCandidateReq(itemId, includeUnavailable, candidateLimit));
            inserts.addAll(buildSuggestions(task, source, risk, candidates));
        }

        if (inserts.isEmpty()) {
            ScheduleRepairSuggestion manual = manualSuggestion(task, null, null, "暂无可执行候选位置，建议人工处理或局部重排");
            inserts.add(manual);
        }
        for (ScheduleRepairSuggestion s : inserts) {
            suggestionMapper.insert(s);
        }
        updateTaskSuggested(task);
        return listByTask(taskId);
    }

    public List<V5RepairSuggestionVo> listByTask(Long taskId) {
        requireTask(taskId);
        List<ScheduleRepairSuggestion> list = suggestionMapper.selectList(new LambdaQueryWrapper<ScheduleRepairSuggestion>()
                .eq(ScheduleRepairSuggestion::getRepairTaskId, taskId)
                .orderByDesc(ScheduleRepairSuggestion::getCreatedAt)
                .orderByDesc(ScheduleRepairSuggestion::getId));
        return list.stream().map(this::toVo).toList();
    }

    public V5RepairSuggestionVo detail(Long taskId, Long suggestionId) {
        requireTask(taskId);
        ScheduleRepairSuggestion s = requireSuggestion(taskId, suggestionId);
        return toVo(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public V5RepairSuggestionVo markForSimulation(Long taskId, Long suggestionId) {
        ScheduleRepairTask task = requireTask(taskId);
        if ("CANCELLED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            throw new BusinessException("任务已结束，不能进入试算");
        }
        ScheduleRepairSuggestion s = requireSuggestion(taskId, suggestionId);
        s.setStatus(V5SuggestionStatus.ACCEPTED.getCode());
        suggestionMapper.updateById(s);
        if (!"SIMULATED".equals(task.getStatus())) {
            task.setStatus("SUGGESTED");
            repairTaskMapper.updateById(task);
        }
        return toVo(s);
    }

    private List<ScheduleRepairSuggestion> buildSuggestions(
            ScheduleRepairTask task,
            SchedulePlanItem source,
            ScheduleRiskIssueVo risk,
            V5CandidatePositionResultVo result
    ) {
        List<V5CandidatePositionVo> available = result.getCandidates() == null ? List.of() :
                result.getCandidates().stream().filter(c -> Boolean.TRUE.equals(c.getAvailable())).toList();
        if (available.isEmpty()) {
            List<ScheduleRepairSuggestion> fallback = new ArrayList<>();
            fallback.add(manualSuggestion(task, source, risk, "无可用候选位置，建议人工处理"));
            if (risk != null && hasMultipleItems(risk)) {
                fallback.add(partialRescheduleSuggestion(task, source, risk, "涉及多课程联动冲突，建议进入局部重排"));
            }
            return fallback;
        }
        V5CandidatePositionVo keepTime = available.stream()
                .filter(c -> Objects.equals(c.getWeekday(), source.getWeekday()))
                .filter(c -> Objects.equals(c.getStartPeriod(), source.getStartPeriod()) && Objects.equals(c.getEndPeriod(), source.getEndPeriod()))
                .filter(c -> !Objects.equals(c.getClassroomId(), source.getClassroomId()))
                .max(Comparator.comparing(V5CandidatePositionVo::getTotalScore, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        V5CandidatePositionVo keepRoom = available.stream()
                .filter(c -> Objects.equals(c.getClassroomId(), source.getClassroomId()))
                .filter(c -> !Objects.equals(c.getWeekday(), source.getWeekday()) || !Objects.equals(c.getStartPeriod(), source.getStartPeriod()))
                .max(Comparator.comparing(V5CandidatePositionVo::getTotalScore, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        V5CandidatePositionVo both = available.stream()
                .filter(c -> !Objects.equals(c.getClassroomId(), source.getClassroomId()))
                .filter(c -> !Objects.equals(c.getWeekday(), source.getWeekday()) || !Objects.equals(c.getStartPeriod(), source.getStartPeriod()))
                .max(Comparator.comparing(V5CandidatePositionVo::getTotalScore, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        List<ScheduleRepairSuggestion> out = new ArrayList<>();
        if (keepTime != null) out.add(buildMoveSuggestion(task, source, risk, keepTime, "KEEP_TIME_CHANGE_ROOM", "HIGH"));
        if (keepRoom != null) out.add(buildMoveSuggestion(task, source, risk, keepRoom, "KEEP_ROOM_CHANGE_TIME", "MEDIUM"));
        if (both != null) out.add(buildMoveSuggestion(task, source, risk, both, "CHANGE_TIME_AND_ROOM", "MEDIUM"));
        if (out.isEmpty()) {
            out.add(manualSuggestion(task, source, risk, "存在候选但未形成稳定建议，建议人工复核"));
        }
        return out;
    }

    private ScheduleRepairSuggestion buildMoveSuggestion(
            ScheduleRepairTask task,
            SchedulePlanItem source,
            ScheduleRiskIssueVo risk,
            V5CandidatePositionVo candidate,
            String type,
            String level
    ) {
        String desc = "建议将课程调整为 周" + candidate.getWeekday() + " " + candidate.getStartPeriod() + "-" + candidate.getEndPeriod()
                + " 节，教室：" + candidate.getClassroomName();
        SuggestionDetail detail = new SuggestionDetail();
        detail.setRiskItemId(risk == null ? null : risk.getId());
        detail.setRiskType(risk == null ? null : risk.getRiskType());
        detail.setSourceWeekday(source.getWeekday());
        detail.setSourceStartPeriod(source.getStartPeriod());
        detail.setSourceEndPeriod(source.getEndPeriod());
        detail.setSourceClassroomId(source.getClassroomId());
        detail.setTargetWeekday(candidate.getWeekday());
        detail.setTargetStartPeriod(candidate.getStartPeriod());
        detail.setTargetEndPeriod(candidate.getEndPeriod());
        detail.setTargetClassroomId(candidate.getClassroomId());
        detail.setResolvesOriginalRisk(Boolean.TRUE);
        detail.setIntroducesNewRisk(candidate.getAffectedItems() != null && !candidate.getAffectedItems().isEmpty());
        detail.setAffectedItems(candidate.getAffectedItems() == null ? List.of() : candidate.getAffectedItems());
        detail.setDescription(desc);

        ScheduleRepairSuggestion s = new ScheduleRepairSuggestion();
        s.setSemesterId(task.getSemesterId());
        s.setPlanId(task.getPlanId());
        s.setRepairTaskId(task.getId());
        s.setSourcePlanId(task.getSourcePlanId());
        s.setSourceScheduleId(task.getSourceScheduleId());
        s.setSourcePlanItemId(source.getId());
        s.setSuggestionCode("RSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT));
        s.setSuggestionType(type);
        s.setStatus(V5SuggestionStatus.PENDING.getCode());
        s.setPriorityLevel(level);
        s.setExpectedScoreDelta(candidate.getTotalScore() == null ? BigDecimal.ZERO : candidate.getTotalScore());
        s.setExpectedRiskDelta(Boolean.TRUE.equals(detail.getIntroducesNewRisk()) ? 0 : -1);
        s.setExpectedUnscheduledDelta(0);
        s.setReasonSummary(candidate.getReason());
        s.setDetailJson(writeJson(detail));
        return s;
    }

    private ScheduleRepairSuggestion manualSuggestion(ScheduleRepairTask task, SchedulePlanItem source, ScheduleRiskIssueVo risk, String reason) {
        SuggestionDetail detail = new SuggestionDetail();
        detail.setRiskItemId(risk == null ? null : risk.getId());
        detail.setRiskType(risk == null ? null : risk.getRiskType());
        if (source != null) {
            detail.setSourceWeekday(source.getWeekday());
            detail.setSourceStartPeriod(source.getStartPeriod());
            detail.setSourceEndPeriod(source.getEndPeriod());
            detail.setSourceClassroomId(source.getClassroomId());
        }
        detail.setResolvesOriginalRisk(false);
        detail.setIntroducesNewRisk(false);
        detail.setAffectedItems(List.of());
        detail.setDescription(reason);

        ScheduleRepairSuggestion s = new ScheduleRepairSuggestion();
        s.setSemesterId(task.getSemesterId());
        s.setPlanId(task.getPlanId());
        s.setRepairTaskId(task.getId());
        s.setSourcePlanId(task.getSourcePlanId());
        s.setSourceScheduleId(task.getSourceScheduleId());
        s.setSourcePlanItemId(source == null ? null : source.getId());
        s.setSuggestionCode("RSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT));
        s.setSuggestionType("MANUAL_REVIEW");
        s.setStatus(V5SuggestionStatus.PENDING.getCode());
        s.setPriorityLevel("MANUAL");
        s.setExpectedScoreDelta(BigDecimal.ZERO);
        s.setExpectedRiskDelta(0);
        s.setExpectedUnscheduledDelta(0);
        s.setReasonSummary(reason);
        s.setDetailJson(writeJson(detail));
        return s;
    }

    private ScheduleRepairSuggestion partialRescheduleSuggestion(ScheduleRepairTask task, SchedulePlanItem source, ScheduleRiskIssueVo risk, String reason) {
        ScheduleRepairSuggestion s = manualSuggestion(task, source, risk, reason);
        s.setSuggestionType("PARTIAL_RESCHEDULE");
        s.setPriorityLevel("LOW");
        return s;
    }

    private void clearTaskSuggestions(Long taskId) {
        suggestionMapper.delete(new LambdaQueryWrapper<ScheduleRepairSuggestion>()
                .eq(ScheduleRepairSuggestion::getRepairTaskId, taskId));
    }

    private void updateTaskSuggested(ScheduleRepairTask task) {
        task.setStatus("SUGGESTED");
        repairTaskMapper.updateById(task);
    }

    private Map<Long, ScheduleRiskIssueVo> loadRiskMap(Long planId, List<Long> riskIds) {
        if (riskIds == null || riskIds.isEmpty()) return Map.of();
        ScheduleRiskListVo vo = riskService.getPlanRisks(planId, null, null, null);
        return vo.getRisks().stream()
                .filter(r -> riskIds.contains(r.getId()))
                .collect(Collectors.toMap(ScheduleRiskIssueVo::getId, r -> r, (a, b) -> a));
    }

    private List<Long> resolveRelatedItemIds(Iterable<ScheduleRiskIssueVo> risks) {
        List<Long> ids = new ArrayList<>();
        for (ScheduleRiskIssueVo risk : risks) {
            if (risk.getRelatedItemIds() != null) ids.addAll(risk.getRelatedItemIds());
        }
        return ids;
    }

    private ScheduleRiskIssueVo matchRiskForItem(Iterable<ScheduleRiskIssueVo> risks, Long itemId) {
        for (ScheduleRiskIssueVo r : risks) {
            if (r.getRelatedItemIds() != null && r.getRelatedItemIds().contains(itemId)) return r;
        }
        return null;
    }

    private boolean hasMultipleItems(ScheduleRiskIssueVo risk) {
        return risk.getRelatedItemIds() != null && risk.getRelatedItemIds().size() > 1;
    }

    private V5CandidatePositionGenerateRequest buildCandidateReq(Long planItemId, boolean includeUnavailable, int limit) {
        V5CandidatePositionGenerateRequest req = new V5CandidatePositionGenerateRequest();
        req.setPlanItemId(planItemId);
        req.setIncludeUnavailable(includeUnavailable);
        req.setLimit(limit);
        return req;
    }

    private V5RepairSuggestionVo toVo(ScheduleRepairSuggestion s) {
        SuggestionDetail d = readDetail(s.getDetailJson());
        V5RepairSuggestionVo vo = new V5RepairSuggestionVo();
        vo.setId(s.getId());
        vo.setRepairTaskId(s.getRepairTaskId());
        vo.setSuggestionCode(s.getSuggestionCode());
        vo.setSuggestionType(s.getSuggestionType());
        vo.setRecommendationLevel(s.getPriorityLevel());
        vo.setStatus(s.getStatus());
        vo.setRiskItemId(d.getRiskItemId());
        vo.setRiskType(d.getRiskType());
        vo.setSourcePlanItemId(s.getSourcePlanItemId());
        vo.setSourceWeekday(d.getSourceWeekday());
        vo.setSourceStartPeriod(d.getSourceStartPeriod());
        vo.setSourceEndPeriod(d.getSourceEndPeriod());
        vo.setSourceClassroomId(d.getSourceClassroomId());
        vo.setSourceClassroomName(classroomName(d.getSourceClassroomId()));
        vo.setTargetWeekday(d.getTargetWeekday());
        vo.setTargetStartPeriod(d.getTargetStartPeriod());
        vo.setTargetEndPeriod(d.getTargetEndPeriod());
        vo.setTargetClassroomId(d.getTargetClassroomId());
        vo.setTargetClassroomName(classroomName(d.getTargetClassroomId()));
        vo.setResolvesOriginalRisk(d.getResolvesOriginalRisk());
        vo.setIntroducesNewRisk(d.getIntroducesNewRisk());
        vo.setAffectedItems(d.getAffectedItems() == null ? List.of() : d.getAffectedItems());
        vo.setExpectedScoreDelta(s.getExpectedScoreDelta());
        vo.setReasonSummary(s.getReasonSummary());
        vo.setDescription(d.getDescription());
        vo.setCreatedAt(s.getCreatedAt());
        return vo;
    }

    private String classroomName(Long classroomId) {
        if (classroomId == null) return null;
        Classroom c = classroomMapper.selectById(classroomId);
        return c == null ? null : c.getRoomName();
    }

    private ScheduleRepairTask requireTask(Long taskId) {
        ScheduleRepairTask task = repairTaskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("修复任务不存在");
        return task;
    }

    private ScheduleRepairSuggestion requireSuggestion(Long taskId, Long suggestionId) {
        ScheduleRepairSuggestion s = suggestionMapper.selectById(suggestionId);
        if (s == null || !Objects.equals(s.getRepairTaskId(), taskId)) {
            throw new BusinessException("修复建议不存在");
        }
        return s;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("修复建议序列化失败");
        }
    }

    private SuggestionDetail readDetail(String json) {
        if (json == null || json.isBlank()) return new SuggestionDetail();
        try {
            return objectMapper.readValue(json, new TypeReference<SuggestionDetail>() {});
        } catch (Exception e) {
            log.warn("反序列化 SuggestionDetail 失败，使用空对象兜底；payload 前 200 字符: {}",
                    json.substring(0, Math.min(200, json.length())), e);
            return new SuggestionDetail();
        }
    }

    private List<Long> readLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("反序列化 List<Long> 失败，使用空列表兜底；payload 前 200 字符: {}",
                    json.substring(0, Math.min(200, json.length())), e);
            return List.of();
        }
    }

    @lombok.Data
    private static class SuggestionDetail {
        private Long riskItemId;
        private String riskType;
        private Integer sourceWeekday;
        private Integer sourceStartPeriod;
        private Integer sourceEndPeriod;
        private Long sourceClassroomId;
        private Integer targetWeekday;
        private Integer targetStartPeriod;
        private Integer targetEndPeriod;
        private Long targetClassroomId;
        private Boolean resolvesOriginalRisk;
        private Boolean introducesNewRisk;
        private List<Long> affectedItems;
        private String description;
    }
}
