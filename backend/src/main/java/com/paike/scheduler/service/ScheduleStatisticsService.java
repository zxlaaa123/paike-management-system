package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.enums.SchedulePlanStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
import com.paike.scheduler.service.vo.ClassBalanceVo;
import com.paike.scheduler.service.vo.ClassroomUtilizationVo;
import com.paike.scheduler.service.vo.DashboardStatsVo;
import com.paike.scheduler.service.vo.PlanOverviewVo;
import com.paike.scheduler.service.vo.TeacherWorkloadVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleStatisticsService {

    private final ScheduleMapper scheduleMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final SchedulePlanMapper planMapper;
    private final ClassroomMapper classroomMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ScheduleThresholdProperties thresholds;

    // ==================== 教师工作量统计 ====================

    public List<TeacherWorkloadVo> teacherWorkload(Long semesterId, Long planId) {
        List<?> rawItems = loadItems(semesterId, planId);
        if (rawItems.isEmpty()) {
            return List.of();
        }

        Map<Long, TeacherWorkloadAcc> teacherMap = new LinkedHashMap<>();
        Map<Long, Teacher> teacherCache = new HashMap<>();
        Map<Long, TimeSlot> timeSlotCache = loadTimeSlotMap(rawItems);

        for (Object obj : rawItems) {
            Long teacherId = getTeacherId(obj);
            Long courseId = getCourseId(obj);
            Long classId = getClassId(obj);
            int periods = getPeriodCount(obj);
            int weekday = getWeekday(obj, timeSlotCache);
            if (weekday <= 0 || weekday > 7) continue;

            TeacherWorkloadAcc acc = teacherMap.computeIfAbsent(teacherId, k -> new TeacherWorkloadAcc());
            acc.totalPeriods += periods;
            acc.courseIds.add(courseId);
            acc.classIds.add(classId);
            acc.dailyPeriods.merge(weekday, (long) periods, ScheduleStatisticsService::sumLongs);
        }

        // 填充关联信息 + 计算衍生指标
        List<TeacherWorkloadVo> result = new ArrayList<>();
        for (Map.Entry<Long, TeacherWorkloadAcc> entry : teacherMap.entrySet()) {
            Long teacherId = entry.getKey();
            TeacherWorkloadAcc acc = entry.getValue();

            Teacher teacher = teacherCache.computeIfAbsent(teacherId, id -> teacherMapper.selectById(id));
            int maxDaily = acc.dailyPeriods.values().stream().mapToInt(Long::intValue).max().orElse(0);
            String evaluation = evaluateWorkload(acc.totalPeriods, maxDaily);

            TeacherWorkloadVo vo = new TeacherWorkloadVo();
            vo.setTeacherId(teacherId);
            vo.setTotalPeriods(acc.totalPeriods);
            vo.setDailyPeriods(acc.dailyPeriods);
            vo.setMaxDailyPeriods(maxDaily);
            vo.setMaxContinuousPeriods(0); // 历史恒为 0（占位字段），保留以维持 JSON 不变
            vo.setCourseCount(acc.courseIds.size());
            vo.setClassCount(acc.classIds.size());
            vo.setTeacherName(teacher != null ? teacher.getName() : "未知");
            vo.setDepartment(teacher != null ? teacher.getDepartment() : null);
            vo.setEvaluation(evaluation);
            result.add(vo);
        }

        return result;
    }

    // ==================== 教室利用率统计 ====================

    public List<ClassroomUtilizationVo> classroomUtilization(Long semesterId, Long planId) {
        List<?> rawItems = loadItems(semesterId, planId);

        // 统计每个教室的使用节次
        Map<Long, Long> roomUsage = new LinkedHashMap<>();
        for (Object obj : rawItems) {
            Long roomId = getRoomId(obj);
            int periods = getPeriodCount(obj);
            roomUsage.merge(roomId, (long) periods, ScheduleStatisticsService::sumLongs);
        }

        List<Classroom> allRooms = classroomMapper.selectList(
                new LambdaQueryWrapper<Classroom>()
                        .eq(Classroom::getStatus, 1));

        List<ClassroomUtilizationVo> result = new ArrayList<>();
        for (Classroom room : allRooms) {
            long usedPeriods = roomUsage.getOrDefault(room.getId(), 0L);
            BigDecimal rate = thresholds.getTotalAvailablePeriods() > 0
                    ? BigDecimal.valueOf(usedPeriods).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(thresholds.getTotalAvailablePeriods()), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            ClassroomUtilizationVo vo = new ClassroomUtilizationVo();
            vo.setRoomId(room.getId());
            vo.setRoomName(room.getRoomName());
            vo.setBuilding(room.getBuilding());
            vo.setCapacity(room.getCapacity());
            vo.setRoomType(room.getRoomType());
            vo.setUsedPeriods(usedPeriods);
            vo.setTotalPeriods(thresholds.getTotalAvailablePeriods());
            vo.setUtilizationRate(rate);
            vo.setEvaluation(evaluateUtilization(rate.doubleValue()));
            result.add(vo);
        }

        // 按利用率降序排序
        result.sort(Comparator.comparing(ClassroomUtilizationVo::getUtilizationRate).reversed());

        return result;
    }

    // ==================== 班级均衡度统计 ====================

    public List<ClassBalanceVo> classBalance(Long semesterId, Long planId) {
        List<?> rawItems = loadItems(semesterId, planId);
        if (rawItems.isEmpty()) {
            return List.of();
        }

        Map<Long, ClassBalanceAcc> classMap = new LinkedHashMap<>();
        Map<Long, ClassInfo> classCache = new HashMap<>();
        Map<Long, TimeSlot> timeSlotCache = loadTimeSlotMap(rawItems);

        for (Object obj : rawItems) {
            Long classId = getClassId(obj);
            int weekday = getWeekday(obj, timeSlotCache);
            int periods = getPeriodCount(obj);
            if (weekday <= 0 || weekday > 7) continue;

            ClassBalanceAcc acc = classMap.computeIfAbsent(classId, k -> new ClassBalanceAcc());
            acc.totalPeriods += periods;
            acc.dailyPeriods.merge(weekday, (long) periods, ScheduleStatisticsService::sumLongs);
        }

        List<ClassBalanceVo> result = new ArrayList<>();
        for (Map.Entry<Long, ClassBalanceAcc> entry : classMap.entrySet()) {
            Long classId = entry.getKey();
            ClassBalanceAcc acc = entry.getValue();

            ClassInfo classInfo = classCache.computeIfAbsent(classId, id -> classInfoMapper.selectById(id));
            int total = acc.totalPeriods;

            // 均衡分 = 1 - (标准差 / 平均)，越接近 1 越均衡
            double balanceScore = calculateBalanceScore(acc.dailyPeriods, total);

            ClassBalanceVo vo = new ClassBalanceVo();
            vo.setClassId(classId);
            vo.setDailyPeriods(acc.dailyPeriods);
            vo.setTotalPeriods(total);
            vo.setClassName(classInfo != null ? classInfo.getClassName() : "未知");
            vo.setStudentCount(classInfo != null ? classInfo.getStudentCount() : 0);
            vo.setBalanceScore(BigDecimal.valueOf(balanceScore).setScale(2, RoundingMode.HALF_UP));
            vo.setEvaluation(evaluateBalance(balanceScore));
            // 每日课时详情
            vo.setDay1Periods(acc.dailyPeriods.getOrDefault(1, 0L));
            vo.setDay2Periods(acc.dailyPeriods.getOrDefault(2, 0L));
            vo.setDay3Periods(acc.dailyPeriods.getOrDefault(3, 0L));
            vo.setDay4Periods(acc.dailyPeriods.getOrDefault(4, 0L));
            vo.setDay5Periods(acc.dailyPeriods.getOrDefault(5, 0L));

            result.add(vo);
        }

        // 按均衡分升序排序（最不均衡的排前面）
        result.sort(Comparator.comparing(ClassBalanceVo::getBalanceScore));

        return result;
    }

    // ==================== 方案统计总览 ====================

    public PlanOverviewVo planOverview(Long semesterId) {
        PlanOverviewVo overview = new PlanOverviewVo();

        // 当前学期信息
        overview.setSemesterId(semesterId);

        // 方案数量统计
        List<SchedulePlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<SchedulePlan>()
                        .eq(SchedulePlan::getSemesterId, semesterId));

        overview.setTotalPlans(plans.size());
        overview.setDraftPlans(plans.stream().filter(p -> SchedulePlanStatus.DRAFT.is(p.getStatus())).count());
        overview.setAppliedPlans(plans.stream().filter(p -> SchedulePlanStatus.APPLIED.is(p.getStatus())).count());
        overview.setAbandonedPlans(plans.stream().filter(p -> SchedulePlanStatus.ABANDONED.is(p.getStatus())).count());

        // 最高评分方案
        SchedulePlan bestPlan = plans.stream()
                .filter(p -> p.getTotalScore() != null)
                .max(Comparator.comparing(SchedulePlan::getTotalScore))
                .orElse(null);
        if (bestPlan != null) {
            overview.setBestPlanId(bestPlan.getId());
            overview.setBestPlanName(bestPlan.getName());
            overview.setBestPlanScore(bestPlan.getTotalScore());
            overview.setBestPlanStrategy(bestPlan.getStrategyType());
        }

        // 当前已应用的方案
        SchedulePlan appliedPlan = plans.stream()
                .filter(p -> SchedulePlanStatus.APPLIED.is(p.getStatus()))
                .findFirst()
                .orElse(null);
        overview.setHasAppliedPlan(appliedPlan != null);
        if (appliedPlan != null) {
            overview.setAppliedPlanId(appliedPlan.getId());
            overview.setAppliedPlanName(appliedPlan.getName());
            overview.setAppliedPlanScore(appliedPlan.getTotalScore());
            overview.setAppliedPlanAppliedAt(appliedPlan.getAppliedAt());
        }

        // 正式课表课程数量
        Long formalCount = scheduleMapper.selectCount(
                new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getSemesterId, semesterId));
        overview.setFormalScheduleCount(formalCount);

        // 未排任务数量（所有方案合计）
        int totalUnassigned = 0;
        int totalConflicts = 0;
        for (SchedulePlan plan : plans) {
            if (plan.getUnscheduledCount() != null) totalUnassigned += plan.getUnscheduledCount();
            if (plan.getConflictCount() != null) totalConflicts += plan.getConflictCount();
        }
        overview.setTotalUnassignedTasks(totalUnassigned);
        overview.setTotalConflicts(totalConflicts);

        return overview;
    }

    // ==================== 首页统计 ====================

    public DashboardStatsVo dashboardStats(Long semesterId) {
        // 基础数据量
        Long teacherCount = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>());
        Long classCount = classInfoMapper.selectCount(new LambdaQueryWrapper<ClassInfo>());
        Long classroomCount = classroomMapper.selectCount(new LambdaQueryWrapper<Classroom>());

        DashboardStatsVo stats = new DashboardStatsVo();
        stats.setTeacherCount(teacherCount);
        stats.setClassCount(classCount);
        stats.setClassroomCount(classroomCount);
        // V3 方案概览
        stats.setV3Overview(planOverview(semesterId));

        return stats;
    }

    // ==================== 辅助方法 ====================

    private List<?> loadItems(Long semesterId, Long planId) {
        if (planId != null) {
            // 统计指定方案
            SchedulePlan plan = planMapper.selectById(planId);
            if (plan == null) {
                throw new BusinessException("排课方案不存在");
            }
            return planItemMapper.selectList(
                    new LambdaQueryWrapper<SchedulePlanItem>()
                            .eq(SchedulePlanItem::getPlanId, planId));
        } else {
            // 统计正式课表
            return scheduleMapper.selectList(
                    new LambdaQueryWrapper<Schedule>()
                            .eq(Schedule::getSemesterId, semesterId));
        }
    }

    private Long getTeacherId(Object obj) {
        if (obj instanceof SchedulePlanItem) return ((SchedulePlanItem) obj).getTeacherId();
        if (obj instanceof Schedule) return ((Schedule) obj).getTeacherId();
        return null;
    }

    private Long getCourseId(Object obj) {
        if (obj instanceof SchedulePlanItem) return ((SchedulePlanItem) obj).getCourseId();
        if (obj instanceof Schedule) return ((Schedule) obj).getCourseId();
        return null;
    }

    private Long getClassId(Object obj) {
        if (obj instanceof SchedulePlanItem) return ((SchedulePlanItem) obj).getClassId();
        if (obj instanceof Schedule) return ((Schedule) obj).getClassId();
        return null;
    }

    private Long getRoomId(Object obj) {
        if (obj instanceof SchedulePlanItem) return ((SchedulePlanItem) obj).getClassroomId();
        if (obj instanceof Schedule) return ((Schedule) obj).getClassroomId();
        return null;
    }

    private Map<Long, TimeSlot> loadTimeSlotMap(List<?> rawItems) {
        List<Long> timeSlotIds = rawItems.stream()
                .filter(Schedule.class::isInstance)
                .map(item -> ((Schedule) item).getTimeSlotId())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (timeSlotIds.isEmpty()) {
            return Map.of();
        }
        return timeSlotMapper.selectBatchIds(timeSlotIds).stream()
                .collect(Collectors.toMap(TimeSlot::getId, Function.identity(), (a, b) -> a));
    }

    private int getWeekday(Object obj, Map<Long, TimeSlot> timeSlotMap) {
        if (obj instanceof SchedulePlanItem) {
            Integer weekday = ((SchedulePlanItem) obj).getWeekday();
            return weekday != null ? weekday : 0;
        }
        if (obj instanceof Schedule) {
            Long timeSlotId = ((Schedule) obj).getTimeSlotId();
            if (timeSlotId != null) {
                TimeSlot slot = timeSlotMap.get(timeSlotId);
                if (slot != null && slot.getDayOfWeek() != null) {
                    return slot.getDayOfWeek();
                }
            }
        }
        return 0;
    }

    private int getPeriodCount(Object obj) {
        if (obj instanceof SchedulePlanItem) {
            SchedulePlanItem item = (SchedulePlanItem) obj;
            return (item.getEndPeriod() != null && item.getStartPeriod() != null)
                    ? item.getEndPeriod() - item.getStartPeriod() + 1 : 0;
        }
        if (obj instanceof Schedule) {
            return 2; // 正式课表每条记录为 1 大节 = 2 小节
        }
        return 0;
    }

    private String evaluateWorkload(int totalPeriods, int maxDaily) {
        if (totalPeriods >= 20) return "超负荷";
        if (maxDaily >= 6) return "日课时偏高";
        if (totalPeriods >= 16) return "正常偏多";
        if (totalPeriods >= 8) return "正常";
        if (totalPeriods > 0) return "较轻";
        return "无排课";
    }

    private String evaluateUtilization(double rate) {
        if (rate >= 80) return "高利用率";
        if (rate >= 50) return "中等利用率";
        if (rate >= 20) return "低利用率";
        if (rate > 0) return "极低利用率";
        return "未使用";
    }

    private double calculateBalanceScore(Map<Integer, Long> dailyPeriods, int total) {
        if (dailyPeriods.isEmpty() || total == 0) return 0;
        double avg = (double) total / 5; // 按 5 天计算
        double variance = 0;
        for (int day = 1; day <= 5; day++) {
            long count = dailyPeriods.getOrDefault(day, 0L);
            variance += Math.pow(count - avg, 2);
        }
        variance /= 5;
        double stdDev = Math.sqrt(variance);
        double cv = avg > 0 ? stdDev / avg : 0; // 变异系数
        return Math.max(0, Math.min(1, 1 - cv)); // 归一化到 0-1
    }

    private static Long sumLongs(Long left, Long right) {
        long safeLeft = left == null ? 0L : left;
        long safeRight = right == null ? 0L : right;
        return safeLeft + safeRight;
    }

    private String evaluateBalance(double score) {
        if (score >= 0.8) return "优秀";
        if (score >= 0.6) return "良好";
        if (score >= 0.4) return "一般";
        if (score >= 0.2) return "较差";
        return "不均衡";
    }

    /** 教师工作量聚合累加器：替代原 Map&lt;String,Object&gt; 行累加（courseCount/classCount 先以 Set 去重，最后落 size）。 */
    private static final class TeacherWorkloadAcc {
        private int totalPeriods;
        private final Map<Integer, Long> dailyPeriods = new LinkedHashMap<>();
        private final Set<Long> courseIds = new HashSet<>();
        private final Set<Long> classIds = new HashSet<>();
    }

    /** 班级均衡度聚合累加器：替代原 Map&lt;String,Object&gt; 行累加。 */
    private static final class ClassBalanceAcc {
        private int totalPeriods;
        private final Map<Integer, Long> dailyPeriods = new LinkedHashMap<>();
    }
}
