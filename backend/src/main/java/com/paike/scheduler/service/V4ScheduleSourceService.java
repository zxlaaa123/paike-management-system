package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleAdjustLog;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.mapper.ScheduleAdjustLogMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.service.vo.ScheduleAdjustLogVo;
import com.paike.scheduler.service.vo.ScheduleAdjustmentLogListVo;
import com.paike.scheduler.service.vo.ScheduleCurrentSourceVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class V4ScheduleSourceService {

    private final ScheduleMapper scheduleMapper;
    private final SchedulePlanMapper schedulePlanMapper;
    private final ScheduleAdjustLogMapper scheduleAdjustLogMapper;
    private final SemesterService semesterService;
    private final SchedulePlanExplainService schedulePlanExplainService;

    public ScheduleCurrentSourceVo getCurrentSource(Long termId) {
        Semester semester = resolveSemester(termId);

        List<Schedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getSemesterId, semester.getId()));

        Long sourcePlanId = schedules.stream()
                .map(Schedule::getPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(planId -> planId, Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.<Long, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);

        SchedulePlan sourcePlan = sourcePlanId == null ? null : schedulePlanMapper.selectById(sourcePlanId);
        int manualAdjustmentCount = Math.toIntExact(scheduleAdjustLogMapper.selectCount(
                new LambdaQueryWrapper<ScheduleAdjustLog>()
                        .eq(ScheduleAdjustLog::getSemesterId, semester.getId())
                        .isNotNull(ScheduleAdjustLog::getScheduleId)));

        ScheduleCurrentSourceVo vo = new ScheduleCurrentSourceVo();
        vo.setTermId(semester.getId());
        vo.setTermName(semester.getName());
        vo.setSourcePlanId(sourcePlan == null ? null : sourcePlan.getId());
        vo.setSourcePlanName(sourcePlan == null ? null : sourcePlan.getName());
        vo.setStrategyCode(sourcePlan == null ? null : sourcePlan.getStrategyType());
        vo.setTotalScore(sourcePlan == null ? null : sourcePlan.getTotalScore());
        vo.setAppliedAt(sourcePlan == null ? null : sourcePlan.getAppliedAt());
        vo.setHasManualAdjustments(manualAdjustmentCount > 0);
        vo.setManualAdjustmentCount(manualAdjustmentCount);
        return vo;
    }

    public ScheduleAdjustmentLogListVo getPlanAdjustmentLogs(Long planId) {
        SchedulePlan plan = schedulePlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }

        List<ScheduleAdjustLogVo> logs = schedulePlanExplainService.listAdjustLogs(plan.getSemesterId(), planId, null, 1, 200).getRecords();
        List<ScheduleAdjustmentLogListVo.Item> items = logs.stream()
                .sorted(Comparator.comparing(ScheduleAdjustLogVo::getCreatedAt).reversed())
                .map(log -> {
                    ScheduleAdjustmentLogListVo.Item item = new ScheduleAdjustmentLogListVo.Item();
                    item.setId(log.getId());
                    item.setTargetType(log.getScheduleId() != null ? "SCHEDULE" : "PLAN");
                    item.setOperationType("ADJUST_TIME_ROOM");
                    item.setCourseName(log.getCourseName());
                    item.setBeforeWeekDay(log.getOldWeekday());
                    item.setBeforePeriod(formatPeriod(log.getOldStartPeriod(), log.getOldEndPeriod()));
                    item.setBeforeRoomName(log.getOldClassroomName());
                    item.setAfterWeekDay(log.getNewWeekday());
                    item.setAfterPeriod(formatPeriod(log.getNewStartPeriod(), log.getNewEndPeriod()));
                    item.setAfterRoomName(log.getNewClassroomName());
                    item.setRemark(log.getAdjustReason());
                    item.setCreatedAt(log.getCreatedAt());
                    return item;
                })
                .toList();

        ScheduleAdjustmentLogListVo vo = new ScheduleAdjustmentLogListVo();
        vo.setPlanId(planId);
        vo.setItems(items);
        return vo;
    }

    private Semester resolveSemester(Long termId) {
        if (termId != null) {
            Semester semester = semesterService.getById(termId);
            if (semester == null) {
                throw new BusinessException("学期不存在");
            }
            return semester;
        }
        Semester semester = semesterService.getCurrentSemester();
        if (semester == null) {
            throw new BusinessException("当前学期不存在");
        }
        return semester;
    }

    private String formatPeriod(Integer startPeriod, Integer endPeriod) {
        if (startPeriod == null || endPeriod == null) {
            return "—";
        }
        return startPeriod + "-" + endPeriod;
    }
}
