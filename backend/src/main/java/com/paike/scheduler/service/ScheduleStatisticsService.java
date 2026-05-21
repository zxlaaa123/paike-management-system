package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.*;
import com.paike.scheduler.mapper.*;
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

    // ==================== 教师工作量统计 ====================

    public List<Map<String, Object>> teacherWorkload(Long semesterId, Long planId) {
        List<?> rawItems = loadItems(semesterId, planId);
        if (rawItems.isEmpty()) {
            return List.of();
        }

        Map<Long, Map<String, Object>> teacherMap = new LinkedHashMap<>();
        Map<Long, Teacher> teacherCache = new HashMap<>();
        Map<Long, Course> courseCache = new HashMap<>();
        Map<Long, ClassInfo> classCache = new HashMap<>();
        Map<Long, TimeSlot> timeSlotCache = loadTimeSlotMap(rawItems);

        for (Object obj : rawItems) {
            Long teacherId = getTeacherId(obj);
            Long courseId = getCourseId(obj);
            Long classId = getClassId(obj);
            int periods = getPeriodCount(obj);
            int weekday = getWeekday(obj, timeSlotCache);
            if (weekday <= 0 || weekday > 7) continue;

            teacherMap.computeIfAbsent(teacherId, k -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("teacherId", k);
                row.put("totalPeriods", 0);
                row.put("dailyPeriods", new LinkedHashMap<Integer, Long>());
                row.put("maxDailyPeriods", 0);
                row.put("maxContinuousPeriods", 0);
                row.put("courseCount", new HashSet<Long>());
                row.put("classCount", new HashSet<Long>());
                return row;
            });

            Map<String, Object> row = teacherMap.get(teacherId);
            row.put("totalPeriods", (int) row.get("totalPeriods") + periods);
            row.computeIfAbsent("courseCount", k -> new HashSet<Long>());
            ((Set<Long>) row.get("courseCount")).add(courseId);
            ((Set<Long>) row.get("classCount")).add(classId);

            Map<Integer, Long> dailyPeriods = (Map<Integer, Long>) row.get("dailyPeriods");
            dailyPeriods.merge(weekday, (long) periods, Long::sum);
        }

        // 填充关联信息 + 计算衍生指标
        for (Map.Entry<Long, Map<String, Object>> entry : teacherMap.entrySet()) {
            Long teacherId = entry.getKey();
            Map<String, Object> row = entry.getValue();

            Teacher teacher = teacherCache.computeIfAbsent(teacherId, id -> teacherMapper.selectById(id));
            row.put("teacherName", teacher != null ? teacher.getName() : "未知");
            row.put("department", teacher != null ? teacher.getDepartment() : null);

            Set<Long> courseIds = (Set<Long>) row.get("courseCount");
            Set<Long> classIds = (Set<Long>) row.get("classCount");
            row.put("courseCount", courseIds.size());
            row.put("classCount", classIds.size());

            Map<Integer, Long> dailyPeriods = (Map<Integer, Long>) row.get("dailyPeriods");
            int maxDaily = dailyPeriods.values().stream().mapToInt(Long::intValue).max().orElse(0);
            row.put("maxDailyPeriods", maxDaily);

            int totalPeriods = (int) row.get("totalPeriods");
            String evaluation = evaluateWorkload(totalPeriods, maxDaily);
            row.put("evaluation", evaluation);
        }

        return new ArrayList<>(teacherMap.values());
    }

    // ==================== 教室利用率统计 ====================

    public List<Map<String, Object>> classroomUtilization(Long semesterId, Long planId) {
        List<?> rawItems = loadItems(semesterId, planId);

        // 统计每个教室的使用节次
        Map<Long, Long> roomUsage = new LinkedHashMap<>();
        for (Object obj : rawItems) {
            Long roomId = getRoomId(obj);
            int periods = getPeriodCount(obj);
            roomUsage.merge(roomId, (long) periods, Long::sum);
        }

        // 总可用节次 = 5天 × 4大节 = 20节/周
        final int TOTAL_AVAILABLE_PERIODS = 20;

        List<Classroom> allRooms = classroomMapper.selectList(
                new LambdaQueryWrapper<Classroom>()
                        .eq(Classroom::getStatus, 1)
                        .eq(Classroom::getDeleted, 0));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Classroom room : allRooms) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomId", room.getId());
            row.put("roomName", room.getRoomName());
            row.put("building", room.getBuilding());
            row.put("capacity", room.getCapacity());
            row.put("roomType", room.getRoomType());

            long usedPeriods = roomUsage.getOrDefault(room.getId(), 0L);
            row.put("usedPeriods", usedPeriods);
            row.put("totalPeriods", TOTAL_AVAILABLE_PERIODS);

            BigDecimal rate = TOTAL_AVAILABLE_PERIODS > 0
                    ? BigDecimal.valueOf(usedPeriods).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(TOTAL_AVAILABLE_PERIODS), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            row.put("utilizationRate", rate);
            row.put("evaluation", evaluateUtilization(rate.doubleValue()));
            result.add(row);
        }

        // 按利用率降序排序
        result.sort((a, b) -> {
            BigDecimal rateA = (BigDecimal) a.get("utilizationRate");
            BigDecimal rateB = (BigDecimal) b.get("utilizationRate");
            return rateB.compareTo(rateA);
        });

        return result;
    }

    // ==================== 班级均衡度统计 ====================

    public List<Map<String, Object>> classBalance(Long semesterId, Long planId) {
        List<?> rawItems = loadItems(semesterId, planId);
        if (rawItems.isEmpty()) {
            return List.of();
        }

        Map<Long, Map<String, Object>> classMap = new LinkedHashMap<>();
        Map<Long, ClassInfo> classCache = new HashMap<>();
        Map<Long, TimeSlot> timeSlotCache = loadTimeSlotMap(rawItems);

        for (Object obj : rawItems) {
            Long classId = getClassId(obj);
            int weekday = getWeekday(obj, timeSlotCache);
            int periods = getPeriodCount(obj);
            if (weekday <= 0 || weekday > 7) continue;

            classMap.computeIfAbsent(classId, k -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("classId", k);
                row.put("dailyPeriods", new LinkedHashMap<Integer, Long>());
                row.put("totalPeriods", 0);
                return row;
            });

            Map<String, Object> row = classMap.get(classId);
            row.put("totalPeriods", (int) row.get("totalPeriods") + periods);
            @SuppressWarnings("unchecked")
            Map<Integer, Long> dailyPeriods = (Map<Integer, Long>) row.get("dailyPeriods");
            dailyPeriods.merge(weekday, (long) periods, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> entry : classMap.entrySet()) {
            Long classId = entry.getKey();
            Map<String, Object> row = entry.getValue();

            ClassInfo classInfo = classCache.computeIfAbsent(classId, id -> classInfoMapper.selectById(id));
            row.put("className", classInfo != null ? classInfo.getClassName() : "未知");
            row.put("studentCount", classInfo != null ? classInfo.getStudentCount() : 0);

            @SuppressWarnings("unchecked")
            Map<Integer, Long> dailyPeriods = (Map<Integer, Long>) row.get("dailyPeriods");
            int total = (int) row.get("totalPeriods");

            // 均衡分 = 1 - (标准差 / 平均)，越接近 1 越均衡
            double balanceScore = calculateBalanceScore(dailyPeriods, total);
            row.put("balanceScore", BigDecimal.valueOf(balanceScore).setScale(2, RoundingMode.HALF_UP));
            row.put("evaluation", evaluateBalance(balanceScore));
            row.put("totalPeriods", total);

            // 每日课时详情
            for (int day = 1; day <= 5; day++) {
                row.put("day" + day + "Periods", dailyPeriods.getOrDefault(day, 0L));
            }

            result.add(row);
        }

        // 按均衡分升序排序（最不均衡的排前面）
        result.sort((a, b) -> {
            BigDecimal scoreA = (BigDecimal) a.get("balanceScore");
            BigDecimal scoreB = (BigDecimal) b.get("balanceScore");
            return scoreA.compareTo(scoreB);
        });

        return result;
    }

    // ==================== 方案统计总览 ====================

    public Map<String, Object> planOverview(Long semesterId) {
        Map<String, Object> overview = new LinkedHashMap<>();

        // 当前学期信息
        overview.put("semesterId", semesterId);

        // 方案数量统计
        List<SchedulePlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<SchedulePlan>()
                        .eq(SchedulePlan::getSemesterId, semesterId));

        overview.put("totalPlans", plans.size());
        overview.put("draftPlans", plans.stream().filter(p -> "DRAFT".equals(p.getStatus())).count());
        overview.put("appliedPlans", plans.stream().filter(p -> "APPLIED".equals(p.getStatus())).count());
        overview.put("abandonedPlans", plans.stream().filter(p -> "ABANDONED".equals(p.getStatus())).count());

        // 最高评分方案
        SchedulePlan bestPlan = plans.stream()
                .filter(p -> p.getTotalScore() != null)
                .max(Comparator.comparing(SchedulePlan::getTotalScore))
                .orElse(null);
        if (bestPlan != null) {
            overview.put("bestPlanId", bestPlan.getId());
            overview.put("bestPlanName", bestPlan.getName());
            overview.put("bestPlanScore", bestPlan.getTotalScore());
            overview.put("bestPlanStrategy", bestPlan.getStrategyType());
        }

        // 当前已应用的方案
        SchedulePlan appliedPlan = plans.stream()
                .filter(p -> "APPLIED".equals(p.getStatus()))
                .findFirst()
                .orElse(null);
        overview.put("hasAppliedPlan", appliedPlan != null);
        if (appliedPlan != null) {
            overview.put("appliedPlanId", appliedPlan.getId());
            overview.put("appliedPlanName", appliedPlan.getName());
            overview.put("appliedPlanScore", appliedPlan.getTotalScore());
            overview.put("appliedPlanAppliedAt", appliedPlan.getAppliedAt());
        }

        // 正式课表课程数量
        Long formalCount = scheduleMapper.selectCount(
                new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getSemesterId, semesterId)
                        .eq(Schedule::getDeleted, 0));
        overview.put("formalScheduleCount", formalCount);

        // 未排任务数量（所有方案合计）
        int totalUnassigned = 0;
        int totalConflicts = 0;
        for (SchedulePlan plan : plans) {
            if (plan.getUnscheduledCount() != null) totalUnassigned += plan.getUnscheduledCount();
            if (plan.getConflictCount() != null) totalConflicts += plan.getConflictCount();
        }
        overview.put("totalUnassignedTasks", totalUnassigned);
        overview.put("totalConflicts", totalConflicts);

        return overview;
    }

    // ==================== 首页统计 ====================

    public Map<String, Object> dashboardStats(Long semesterId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 基础数据量
        Long teacherCount = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>().eq(Teacher::getDeleted, 0));
        Long classCount = classInfoMapper.selectCount(new LambdaQueryWrapper<ClassInfo>().eq(ClassInfo::getDeleted, 0));
        Long classroomCount = classroomMapper.selectCount(new LambdaQueryWrapper<Classroom>().eq(Classroom::getDeleted, 0));

        stats.put("teacherCount", teacherCount);
        stats.put("classCount", classCount);
        stats.put("classroomCount", classroomCount);

        // V3 方案概览
        Map<String, Object> planOverview = planOverview(semesterId);
        stats.put("v3Overview", planOverview);

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
                            .eq(Schedule::getSemesterId, semesterId)
                            .eq(Schedule::getDeleted, 0));
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
        if (obj instanceof SchedulePlanItem) return ((SchedulePlanItem) obj).getWeekday();
        if (obj instanceof Schedule) {
            Long timeSlotId = ((Schedule) obj).getTimeSlotId();
            if (timeSlotId != null) {
                TimeSlot slot = timeSlotMap.get(timeSlotId);
                return slot != null ? slot.getDayOfWeek() : 0;
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

    private String evaluateBalance(double score) {
        if (score >= 0.8) return "优秀";
        if (score >= 0.6) return "良好";
        if (score >= 0.4) return "一般";
        if (score >= 0.2) return "较差";
        return "不均衡";
    }
}
