package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.SemesterMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.vo.ScheduleAnalysisSummaryVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class V4ScheduleAnalysisService {

    private final SchedulePlanMapper schedulePlanMapper;
    private final SchedulePlanItemMapper schedulePlanItemMapper;
    private final SemesterMapper semesterMapper;
    private final TimeSlotMapper timeSlotMapper;

    public ScheduleAnalysisSummaryVo getPlanSummary(Long planId) {
        SchedulePlan plan = schedulePlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }

        List<SchedulePlanItem> items = schedulePlanItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, planId)
                        .orderByAsc(SchedulePlanItem::getWeekday)
                        .orderByAsc(SchedulePlanItem::getStartPeriod));

        Semester semester = semesterMapper.selectById(plan.getSemesterId());
        Semester currentSemester = semesterMapper.selectOne(
                new LambdaQueryWrapper<Semester>().eq(Semester::getIsCurrent, 1));
        long totalTimeSlotCount = timeSlotMapper.selectCount(new LambdaQueryWrapper<TimeSlot>());
        int totalTimeSlots = (int) Math.max(totalTimeSlotCount, 1L);

        Set<Long> teacherIds = new LinkedHashSet<>();
        Set<Long> classIds = new LinkedHashSet<>();
        Set<Long> roomIds = new LinkedHashSet<>();
        Set<Long> courseIds = new LinkedHashSet<>();

        Map<Long, Integer> teacherLoads = new HashMap<>();
        Map<Long, Integer> roomLoads = new HashMap<>();
        Map<String, Integer> classDailyLoads = new HashMap<>();

        int totalPeriods = 0;
        int overloadedTeacherCount = 0;
        int overloadedClassDayCount = 0;
        int overloadedRoomCount = 0;
        int underutilizedRoomCount = 0;

        for (SchedulePlanItem item : items) {
            int duration = lessonPeriods(item);
            totalPeriods += duration;

            teacherIds.add(item.getTeacherId());
            classIds.add(item.getClassId());
            roomIds.add(item.getClassroomId());
            courseIds.add(item.getCourseId());

            teacherLoads.merge(item.getTeacherId(), duration, Integer::sum);
            roomLoads.merge(item.getClassroomId(), duration, Integer::sum);
            classDailyLoads.merge(item.getClassId() + "_" + item.getWeekday(), duration, Integer::sum);
        }

        for (Integer load : teacherLoads.values()) {
            if (load >= 18) {
                overloadedTeacherCount++;
            }
        }

        for (Integer load : classDailyLoads.values()) {
            if (load >= 8) {
                overloadedClassDayCount++;
            }
        }

        int roomDenominator = Math.max(roomIds.size() * totalTimeSlots * 2, 1);
        BigDecimal roomUtilizationRate = percent(totalPeriods, roomDenominator);

        for (Integer load : roomLoads.values()) {
            BigDecimal roomRate = percent(load, totalTimeSlots * 2);
            if (roomRate.compareTo(BigDecimal.valueOf(85)) >= 0) {
                overloadedRoomCount++;
            } else if (roomRate.compareTo(BigDecimal.valueOf(30)) < 0) {
                underutilizedRoomCount++;
            }
        }

        BigDecimal teacherAverageHours = average(teacherLoads.values());
        Integer teacherMaxHours = teacherLoads.values().stream().max(Comparator.naturalOrder()).orElse(0);
        Integer teacherMinHours = teacherLoads.values().stream().min(Comparator.naturalOrder()).orElse(0);
        BigDecimal classAverageDailyLessons = average(classDailyLoads.values());

        int highRiskCount = safeInt(plan.getUnscheduledCount()) + safeInt(plan.getConflictCount());
        int mediumRiskCount = overloadedTeacherCount + overloadedClassDayCount;
        int lowRiskCount = overloadedRoomCount + underutilizedRoomCount;

        ScheduleAnalysisSummaryVo vo = new ScheduleAnalysisSummaryVo();
        vo.setPlanId(plan.getId());
        vo.setPlanName(plan.getName());
        vo.setTermId(plan.getSemesterId());
        vo.setTermName(semester == null ? "未知学期" : semester.getName());
        vo.setStrategyCode(plan.getStrategyType());
        vo.setPlanStatus(plan.getStatus());
        vo.setIsCurrent(currentSemester != null && currentSemester.getId().equals(plan.getSemesterId()) && "APPLIED".equals(plan.getStatus()));
        vo.setTotalScore(plan.getTotalScore());
        vo.setScheduledCount(safeInt(plan.getScheduledCount()));
        vo.setUnscheduledCount(safeInt(plan.getUnscheduledCount()));
        vo.setConflictCount(safeInt(plan.getConflictCount()));
        vo.setTeacherCount(teacherIds.size());
        vo.setClassCount(classIds.size());
        vo.setRoomCount(roomIds.size());
        vo.setCourseCount(courseIds.size());
        vo.setTeacherAverageHours(teacherAverageHours);
        vo.setTeacherMaxHours(teacherMaxHours);
        vo.setTeacherMinHours(teacherMinHours);
        vo.setRoomUtilizationRate(roomUtilizationRate);
        vo.setClassAverageDailyLessons(classAverageDailyLessons);
        vo.setHighRiskCount(highRiskCount);
        vo.setMediumRiskCount(mediumRiskCount);
        vo.setLowRiskCount(lowRiskCount);
        vo.setQualityLevel(resolveQualityLevel(plan, highRiskCount, mediumRiskCount));
        vo.setQualitySummary(buildQualitySummary(plan, roomUtilizationRate, teacherAverageHours, highRiskCount, mediumRiskCount));
        vo.setSuggestions(buildSuggestions(plan, roomUtilizationRate, teacherAverageHours, teacherMaxHours, highRiskCount, mediumRiskCount, underutilizedRoomCount));
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setAppliedAt(plan.getAppliedAt());
        return vo;
    }

    private int lessonPeriods(SchedulePlanItem item) {
        if (item.getStartPeriod() == null || item.getEndPeriod() == null) {
            return 0;
        }
        return Math.max(item.getEndPeriod() - item.getStartPeriod() + 1, 0);
    }

    private BigDecimal percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal average(Iterable<Integer> values) {
        int count = 0;
        int total = 0;
        for (Integer value : values) {
            if (value == null) {
                continue;
            }
            count++;
            total += value;
        }
        if (count == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveQualityLevel(SchedulePlan plan, int highRiskCount, int mediumRiskCount) {
        BigDecimal totalScore = plan.getTotalScore() == null ? BigDecimal.ZERO : plan.getTotalScore();
        if (highRiskCount == 0 && mediumRiskCount <= 1 && totalScore.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "优秀";
        }
        if (highRiskCount <= 1 && totalScore.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return "良好";
        }
        if (highRiskCount <= 3 && totalScore.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "可用";
        }
        return "需优化";
    }

    private String buildQualitySummary(
            SchedulePlan plan,
            BigDecimal roomUtilizationRate,
            BigDecimal teacherAverageHours,
            int highRiskCount,
            int mediumRiskCount
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("方案总分 " + (plan.getTotalScore() == null ? "暂无" : plan.getTotalScore().stripTrailingZeros().toPlainString()));
        parts.add("高风险 " + highRiskCount + " 项");
        parts.add("中风险 " + mediumRiskCount + " 项");
        parts.add("教师平均负载 " + teacherAverageHours.stripTrailingZeros().toPlainString() + " 节");
        parts.add("教室利用率 " + roomUtilizationRate.stripTrailingZeros().toPlainString() + "%");
        return String.join("，", parts);
    }

    private List<String> buildSuggestions(
            SchedulePlan plan,
            BigDecimal roomUtilizationRate,
            BigDecimal teacherAverageHours,
            Integer teacherMaxHours,
            int highRiskCount,
            int mediumRiskCount,
            int underutilizedRoomCount
    ) {
        List<String> suggestions = new ArrayList<>();
        if (safeInt(plan.getUnscheduledCount()) > 0) {
            suggestions.add("优先处理未排任务，避免分析结果只停留在整体评分层面。");
        }
        if (safeInt(plan.getConflictCount()) > 0) {
            suggestions.add("先回到 V3 方案详情检查冲突明细，再进入 V4 风险诊断阶段定位问题。");
        }
        if (roomUtilizationRate.compareTo(BigDecimal.valueOf(40)) < 0) {
            suggestions.add("当前教室利用率偏低，可以在后续图表阶段关注低使用率教室的分布。");
        }
        if (teacherMaxHours != null && teacherAverageHours.compareTo(BigDecimal.ZERO) > 0
                && BigDecimal.valueOf(teacherMaxHours).compareTo(teacherAverageHours.multiply(BigDecimal.valueOf(1.6))) > 0) {
            suggestions.add("部分教师负载明显高于平均值，建议后续检查教师课时均衡性。");
        }
        if (mediumRiskCount > 0 && suggestions.size() < 3) {
            suggestions.add("建议继续进入 V4 阶段 2 和阶段 3，补充评分解释与风险诊断。");
        }
        if (underutilizedRoomCount > 0 && suggestions.size() < 4) {
            suggestions.add("存在低利用率教室，可结合后续图表分析判断是否有容量或类型约束。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("当前方案基础质量较稳定，可以继续查看评分拆解和图表分析。");
        }
        return suggestions;
    }
}
