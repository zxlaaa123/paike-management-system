package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleLockedItem;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.TeacherUnavailableTimeService;
import com.paike.scheduler.service.dto.V5CandidateEvaluateRequest;
import com.paike.scheduler.service.dto.V5CandidatePositionGenerateRequest;
import com.paike.scheduler.service.vo.V5CandidateEvaluationVo;
import com.paike.scheduler.service.vo.V5CandidatePositionResultVo;
import com.paike.scheduler.service.vo.V5CandidatePositionVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class V5CandidatePositionService {

    private final SchedulePlanMapper schedulePlanMapper;
    private final SchedulePlanItemMapper schedulePlanItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final ScheduleLockedItemMapper scheduleLockedItemMapper;
    private final TeacherUnavailableTimeService unavailableTimeService;
    private final V5RuleEvaluationService ruleEvaluationService;

    public V5CandidatePositionResultVo generate(V5CandidatePositionGenerateRequest request) {
        if (request == null || (request.getPlanItemId() == null && request.getScheduleId() == null)) {
            throw new BusinessException("planItemId 或 scheduleId 必须至少传一个");
        }
        SourceContext source = request.getPlanItemId() != null
                ? resolveByPlanItem(request.getPlanItemId())
                : resolveBySchedule(request.getScheduleId());

        List<TimeSlot> timeSlots = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
                .orderByAsc(TimeSlot::getDayOfWeek)
                .orderByAsc(TimeSlot::getPeriodNo));
        List<Classroom> classrooms = classroomMapper.selectList(new LambdaQueryWrapper<Classroom>()
                .eq(Classroom::getDeleted, 0)
                .eq(Classroom::getStatus, 1)
                .orderByAsc(Classroom::getRoomName));
        if (timeSlots.isEmpty() || classrooms.isEmpty()) {
            throw new BusinessException("时间段或教室数据为空，无法生成候选位置");
        }

        boolean includeUnavailable = !Boolean.FALSE.equals(request.getIncludeUnavailable());
        int limit = request.getLimit() == null || request.getLimit() <= 0 ? 300 : Math.min(request.getLimit(), 1000);
        int evaluateBudget = includeUnavailable ? Math.min(Math.max(limit * 6, 1), 480) : Math.min(Math.max(limit * 20, 1), 1200);
        List<SchedulePlanItem> planItems = schedulePlanItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, source.planId));
        Set<Long> lockedIds = scheduleLockedItemMapper.selectList(new LambdaQueryWrapper<ScheduleLockedItem>()
                        .eq(ScheduleLockedItem::getPlanId, source.planId)
                        .eq(ScheduleLockedItem::getActiveFlag, 1))
                .stream()
                .map(ScheduleLockedItem::getPlanItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        boolean sourceLocked = lockedIds.contains(source.planItem.getId());

        List<V5CandidatePositionVo> all = new ArrayList<>();
        boolean stop = false;
        int evaluated = 0;
        for (TimeSlot slot : timeSlots) {
            Integer start = slot.getPeriodNo() * 2 - 1;
            Integer end = start + 1;
            for (Classroom room : classrooms) {
                if (evaluated >= evaluateBudget) {
                    stop = true;
                    break;
                }
                HardCheck hard = fastHardCheck(source.planItem, room, slot, planItems, lockedIds, sourceLocked);
                if (!includeUnavailable && !hard.available) {
                    continue;
                }

                if (!hard.available && includeUnavailable) {
                    V5CandidatePositionVo vo = new V5CandidatePositionVo();
                    vo.setWeekday(slot.getDayOfWeek());
                    vo.setStartPeriod(start);
                    vo.setEndPeriod(end);
                    vo.setClassroomId(room.getId());
                    vo.setClassroomName(room.getRoomName());
                    vo.setAvailable(false);
                    vo.setHardConflictCount(hard.hardConflictCount);
                    vo.setSoftScore(BigDecimal.ZERO);
                    vo.setTotalScore(BigDecimal.ZERO);
                    vo.setReason("不可用：" + hard.reason);
                    vo.setAffectedItems(hard.affectedItems);
                    all.add(vo);
                    evaluated++;
                    continue;
                }
                V5CandidateEvaluateRequest evalReq = new V5CandidateEvaluateRequest();
                evalReq.setPlanId(source.planId);
                evalReq.setPlanItemId(source.planItem.getId());
                evalReq.setCandidateWeekday(slot.getDayOfWeek());
                evalReq.setCandidateStartPeriod(start);
                evalReq.setCandidateEndPeriod(end);
                evalReq.setCandidateClassroomId(room.getId());
                evalReq.setSimulationOnly(true);
                evalReq.setSourcePlanId(source.planId);

                V5CandidateEvaluationVo eval = ruleEvaluationService.evaluateCandidate(evalReq);
                evaluated++;
                if (!includeUnavailable && !Boolean.TRUE.equals(eval.getAvailable())) {
                    continue;
                }

                List<Long> affectedItems = collectAffectedItems(source.planItem, planItems, slot.getDayOfWeek(), start, end, room.getId());
                V5CandidatePositionVo vo = new V5CandidatePositionVo();
                vo.setWeekday(slot.getDayOfWeek());
                vo.setStartPeriod(start);
                vo.setEndPeriod(end);
                vo.setClassroomId(room.getId());
                vo.setClassroomName(room.getRoomName());
                vo.setAvailable(Boolean.TRUE.equals(eval.getAvailable()));
                vo.setHardConflictCount(eval.getHardViolationCount());
                vo.setSoftScore(eval.getSoftScoreDelta());
                vo.setTotalScore(eval.getTotalScoreDelta());
                vo.setReason(buildReason(eval));
                vo.setAffectedItems(affectedItems);
                all.add(vo);
                if (!includeUnavailable && all.size() >= limit) {
                    stop = true;
                    break;
                }
            }
            if (stop) break;
        }

        List<V5CandidatePositionVo> limited;
        if (!includeUnavailable) {
            limited = all;
        } else {
            all.sort(Comparator
                    .comparing(V5CandidatePositionVo::getAvailable).reversed()
                    .thenComparing(V5CandidatePositionVo::getTotalScore, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(V5CandidatePositionVo::getHardConflictCount, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(V5CandidatePositionVo::getWeekday, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(V5CandidatePositionVo::getStartPeriod, Comparator.nullsLast(Integer::compareTo)));
            limited = all.size() > limit ? all.subList(0, limit) : all;
        }
        int availableCount = (int) limited.stream().filter(V5CandidatePositionVo::getAvailable).count();

        V5CandidatePositionResultVo result = new V5CandidatePositionResultVo();
        result.setSemesterId(source.semesterId);
        result.setPlanId(source.planId);
        result.setPlanItemId(source.planItem.getId());
        result.setScheduleId(source.scheduleId);
        result.setSourceWeekday(source.planItem.getWeekday());
        result.setSourceStartPeriod(source.planItem.getStartPeriod());
        result.setSourceEndPeriod(source.planItem.getEndPeriod());
        result.setSourceClassroomId(source.planItem.getClassroomId());
        result.setSourceClassroomName(source.sourceClassroomName);
        result.setTotalCount(limited.size());
        result.setAvailableCount(availableCount);
        result.setCandidates(limited);
        return result;
    }

    private SourceContext resolveByPlanItem(Long planItemId) {
        SchedulePlanItem planItem = schedulePlanItemMapper.selectById(planItemId);
        if (planItem == null) {
            throw new BusinessException("方案明细不存在");
        }
        SchedulePlan plan = schedulePlanMapper.selectById(planItem.getPlanId());
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        Classroom sourceRoom = planItem.getClassroomId() == null ? null : classroomMapper.selectById(planItem.getClassroomId());
        SourceContext context = new SourceContext();
        context.semesterId = plan.getSemesterId();
        context.planId = plan.getId();
        context.planItem = planItem;
        context.sourceClassroomName = sourceRoom == null ? null : sourceRoom.getRoomName();
        return context;
    }

    private SourceContext resolveBySchedule(Long scheduleId) {
        if (scheduleId == null) {
            throw new BusinessException("scheduleId 不能为空");
        }
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || Integer.valueOf(1).equals(schedule.getDeleted())) {
            throw new BusinessException("正式课表记录不存在");
        }
        if (schedule.getPlanId() == null) {
            throw new BusinessException("该正式课表记录未关联方案，暂不支持生成候选位置");
        }
        SchedulePlan plan = schedulePlanMapper.selectById(schedule.getPlanId());
        if (plan == null) {
            throw new BusinessException("来源方案不存在");
        }
        if (!Objects.equals(plan.getSemesterId(), schedule.getSemesterId())) {
            throw new BusinessException("来源课表与方案学期不一致");
        }

        TimeSlot slot = schedule.getTimeSlotId() == null ? null : timeSlotMapper.selectById(schedule.getTimeSlotId());
        Integer weekday = slot == null ? null : slot.getDayOfWeek();
        Integer start = slot == null ? null : slot.getPeriodNo() * 2 - 1;
        Integer end = start == null ? null : start + 1;

        SchedulePlanItem planItem = schedulePlanItemMapper.selectOne(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, plan.getId())
                .eq(SchedulePlanItem::getTeachingTaskId, schedule.getTeachingTaskId())
                .eq(weekday != null, SchedulePlanItem::getWeekday, weekday)
                .eq(start != null, SchedulePlanItem::getStartPeriod, start)
                .eq(end != null, SchedulePlanItem::getEndPeriod, end)
                .last("limit 1"));
        if (planItem == null) {
            throw new BusinessException("未找到对应方案明细，请改用 plan_item_id 方式生成候选位置");
        }
        Classroom sourceRoom = planItem.getClassroomId() == null ? null : classroomMapper.selectById(planItem.getClassroomId());
        SourceContext context = new SourceContext();
        context.semesterId = plan.getSemesterId();
        context.planId = plan.getId();
        context.planItem = planItem;
        context.scheduleId = scheduleId;
        context.sourceClassroomName = sourceRoom == null ? null : sourceRoom.getRoomName();
        return context;
    }

    private List<Long> collectAffectedItems(
            SchedulePlanItem target,
            List<SchedulePlanItem> all,
            Integer weekday,
            Integer start,
            Integer end,
            Long roomId
    ) {
        return all.stream()
                .filter(other -> !Objects.equals(other.getId(), target.getId()))
                .filter(other -> Objects.equals(other.getWeekday(), weekday))
                .filter(other -> overlap(start, end, other.getStartPeriod(), other.getEndPeriod()))
                .filter(other ->
                        Objects.equals(other.getTeacherId(), target.getTeacherId())
                                || Objects.equals(other.getClassId(), target.getClassId())
                                || Objects.equals(other.getClassroomId(), roomId))
                .map(SchedulePlanItem::getId)
                .distinct()
                .toList();
    }

    private boolean overlap(Integer aStart, Integer aEnd, Integer bStart, Integer bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) return false;
        return !(aEnd < bStart || bEnd < aStart);
    }

    private String buildReason(V5CandidateEvaluationVo eval) {
        if (Boolean.TRUE.equals(eval.getAvailable())) {
            return "可用：" + (eval.getSummary() == null ? "" : eval.getSummary());
        }
        String detail = eval.getDetails() == null ? "" : eval.getDetails().stream()
                .filter(d -> Boolean.FALSE.equals(d.getPassed()) && Boolean.TRUE.equals(d.getBlocking()))
                .map(d -> d.getRuleName() + " - " + d.getMessage())
                .findFirst()
                .orElse(eval.getSummary());
        return "不可用：" + detail;
    }

    private HardCheck fastHardCheck(
            SchedulePlanItem target,
            Classroom room,
            TimeSlot slot,
            List<SchedulePlanItem> all,
            Set<Long> lockedIds,
            boolean sourceLocked
    ) {
        HardCheck result = new HardCheck();
        result.available = true;
        result.hardConflictCount = 0;
        result.affectedItems = new ArrayList<>();
        if (sourceLocked) {
            result.available = false;
            result.hardConflictCount = 1;
            result.reason = "锁定课程不可移动";
            return result;
        }
        Integer weekday = slot.getDayOfWeek();
        Integer start = slot.getPeriodNo() * 2 - 1;
        Integer end = start + 1;

        List<SchedulePlanItem> overlaps = all.stream()
                .filter(other -> !Objects.equals(other.getId(), target.getId()))
                .filter(other -> Objects.equals(other.getWeekday(), weekday))
                .filter(other -> overlap(start, end, other.getStartPeriod(), other.getEndPeriod()))
                .toList();
        boolean teacherConflict = overlaps.stream().anyMatch(other -> Objects.equals(other.getTeacherId(), target.getTeacherId()));
        boolean classConflict = overlaps.stream().anyMatch(other -> Objects.equals(other.getClassId(), target.getClassId()));
        boolean roomConflict = overlaps.stream().anyMatch(other -> Objects.equals(other.getClassroomId(), room.getId()));
        boolean unavailable = target.getTeacherId() != null && unavailableTimeService.isUnavailable(target.getTeacherId(), slot.getId());
        boolean capacityViolation = false;
        if (target.getClassId() != null) {
            Long classCount = all.stream()
                    .filter(i -> Objects.equals(i.getClassId(), target.getClassId()))
                    .map(SchedulePlanItem::getId)
                    .findFirst()
                    .map(id -> 0L).orElse(0L);
            capacityViolation = classCount > 0 && room.getCapacity() != null && classCount > room.getCapacity();
        }
        boolean lockedRoomConflict = overlaps.stream()
                .filter(other -> lockedIds.contains(other.getId()))
                .anyMatch(other -> Objects.equals(other.getClassroomId(), room.getId()));
        int fail = 0;
        if (teacherConflict) fail++;
        if (classConflict) fail++;
        if (roomConflict) fail++;
        if (unavailable) fail++;
        if (capacityViolation) fail++;
        if (lockedRoomConflict) fail++;
        result.hardConflictCount = fail;
        result.affectedItems = overlaps.stream().map(SchedulePlanItem::getId).limit(6).toList();
        if (fail > 0) {
            result.available = false;
            if (teacherConflict) result.reason = "教师时间冲突";
            else if (classConflict) result.reason = "班级时间冲突";
            else if (roomConflict) result.reason = "教室时间冲突";
            else if (unavailable) result.reason = "教师禁排时间";
            else if (capacityViolation) result.reason = "教室容量不足";
            else result.reason = "锁定课程占用冲突";
        } else {
            result.reason = "硬约束通过";
        }
        return result;
    }

    private static class HardCheck {
        private boolean available;
        private int hardConflictCount;
        private String reason;
        private List<Long> affectedItems;
    }

    private static class SourceContext {
        private Long semesterId;
        private Long planId;
        private Long scheduleId;
        private String sourceClassroomName;
        private SchedulePlanItem planItem;
    }
}
