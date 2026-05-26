package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.config.ScheduleThresholdProperties;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.service.vo.ScheduleClassDailyLoadChartVo;
import com.paike.scheduler.service.vo.ScheduleRoomUtilizationChartVo;
import com.paike.scheduler.service.vo.ScheduleScoreRadarChartVo;
import com.paike.scheduler.service.vo.ScheduleTeacherHoursChartVo;
import com.paike.scheduler.service.vo.ScheduleTimeDensityChartVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class V4ScheduleChartService {

    private final SchedulePlanMapper schedulePlanMapper;
    private final SchedulePlanItemMapper schedulePlanItemMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ScheduleScoreService scheduleScoreService;
    private final ScheduleThresholdProperties thresholds;

    public ScheduleTeacherHoursChartVo getTeacherHours(Long planId) {
        List<SchedulePlanItem> items = loadPlanItems(planId);
        Map<Long, Teacher> teacherMap = teacherMapper.selectBatchIds(collectIds(items, SchedulePlanItem::getTeacherId))
                .stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));

        Map<Long, Integer> teacherHours = new LinkedHashMap<>();
        Map<Long, Set<Long>> teacherCourseIds = new HashMap<>();
        for (SchedulePlanItem item : items) {
            teacherHours.merge(item.getTeacherId(), lessonPeriods(item), V4ScheduleChartService::sumIntegers);
            teacherCourseIds.computeIfAbsent(item.getTeacherId(), key -> new java.util.LinkedHashSet<>()).add(item.getCourseId());
        }

        List<ScheduleTeacherHoursChartVo.Item> result = teacherHours.entrySet().stream()
                .map(entry -> {
                    Teacher teacher = teacherMap.get(entry.getKey());
                    ScheduleTeacherHoursChartVo.Item row = new ScheduleTeacherHoursChartVo.Item();
                    row.setTeacherId(entry.getKey());
                    row.setTeacherName(teacher == null ? "未知教师" : teacher.getName());
                    row.setTotalHours(entry.getValue());
                    row.setCourseCount(teacherCourseIds.getOrDefault(entry.getKey(), Set.of()).size());
                    return row;
                })
                .sorted(Comparator.comparing(ScheduleTeacherHoursChartVo.Item::getTotalHours).reversed()
                        .thenComparing(ScheduleTeacherHoursChartVo.Item::getTeacherName))
                .toList();

        ScheduleTeacherHoursChartVo vo = new ScheduleTeacherHoursChartVo();
        vo.setPlanId(planId);
        vo.setItems(result);
        return vo;
    }

    public ScheduleRoomUtilizationChartVo getRoomUtilization(Long planId) {
        List<SchedulePlanItem> items = loadPlanItems(planId);
        Map<Long, Classroom> roomMap = classroomMapper.selectBatchIds(collectIds(items, SchedulePlanItem::getClassroomId))
                .stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));

        Map<Long, Integer> roomHours = new LinkedHashMap<>();
        for (SchedulePlanItem item : items) {
            roomHours.merge(item.getClassroomId(), lessonPeriods(item), V4ScheduleChartService::sumIntegers);
        }

        List<ScheduleRoomUtilizationChartVo.Item> result = roomHours.entrySet().stream()
                .map(entry -> {
                    Classroom room = roomMap.get(entry.getKey());
                    ScheduleRoomUtilizationChartVo.Item row = new ScheduleRoomUtilizationChartVo.Item();
                    row.setRoomId(entry.getKey());
                    row.setRoomName(room == null ? "未知教室" : room.getRoomName());
                    row.setRoomType(roomTypeText(room == null ? null : room.getRoomType()));
                    row.setCapacity(room == null ? null : room.getCapacity());
                    row.setUsedPeriods(entry.getValue());
                    row.setTotalPeriods(thresholds.getTotalAvailablePeriods());
                    row.setUtilizationRate(percent(entry.getValue(), thresholds.getTotalAvailablePeriods()));
                    return row;
                })
                .sorted(Comparator.comparing(ScheduleRoomUtilizationChartVo.Item::getUtilizationRate).reversed())
                .toList();

        ScheduleRoomUtilizationChartVo vo = new ScheduleRoomUtilizationChartVo();
        vo.setPlanId(planId);
        vo.setItems(result);
        return vo;
    }

    public ScheduleClassDailyLoadChartVo getClassDailyLoad(Long planId) {
        List<SchedulePlanItem> items = loadPlanItems(planId);
        Map<Long, ClassInfo> classMap = classInfoMapper.selectBatchIds(collectIds(items, SchedulePlanItem::getClassId))
                .stream()
                .collect(Collectors.toMap(ClassInfo::getId, Function.identity(), (a, b) -> a));

        Map<String, Integer> classDailyLoad = new LinkedHashMap<>();
        for (SchedulePlanItem item : items) {
            classDailyLoad.merge(item.getClassId() + "_" + item.getWeekday(), lessonPeriods(item), V4ScheduleChartService::sumIntegers);
        }

        List<ScheduleClassDailyLoadChartVo.Item> result = classDailyLoad.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("_");
                    Long classId = Long.parseLong(parts[0]);
                    Integer weekDay = Integer.parseInt(parts[1]);
                    ClassInfo classInfo = classMap.get(classId);
                    ScheduleClassDailyLoadChartVo.Item row = new ScheduleClassDailyLoadChartVo.Item();
                    row.setClassId(classId);
                    row.setClassName(classInfo == null ? "未知班级" : classInfo.getClassName());
                    row.setWeekDay(weekDay);
                    row.setLessonCount(entry.getValue());
                    return row;
                })
                .sorted(Comparator.comparing(ScheduleClassDailyLoadChartVo.Item::getClassName)
                        .thenComparing(ScheduleClassDailyLoadChartVo.Item::getWeekDay))
                .toList();

        ScheduleClassDailyLoadChartVo vo = new ScheduleClassDailyLoadChartVo();
        vo.setPlanId(planId);
        vo.setItems(result);
        return vo;
    }

    public ScheduleTimeDensityChartVo getTimeDensity(Long planId) {
        List<SchedulePlanItem> items = loadPlanItems(planId);
        Map<String, Integer> density = new LinkedHashMap<>();
        for (SchedulePlanItem item : items) {
            if (item.getWeekday() == null || item.getStartPeriod() == null || item.getEndPeriod() == null) {
                continue;
            }
            for (int period = item.getStartPeriod(); period <= item.getEndPeriod(); period++) {
                density.merge(item.getWeekday() + "_" + period, 1, V4ScheduleChartService::sumIntegers);
            }
        }

        List<ScheduleTimeDensityChartVo.Item> result = density.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("_");
                    ScheduleTimeDensityChartVo.Item row = new ScheduleTimeDensityChartVo.Item();
                    row.setWeekDay(Integer.parseInt(parts[0]));
                    row.setPeriod(Integer.parseInt(parts[1]));
                    row.setCourseCount(entry.getValue());
                    return row;
                })
                .sorted(Comparator.comparing(ScheduleTimeDensityChartVo.Item::getWeekDay)
                        .thenComparing(ScheduleTimeDensityChartVo.Item::getPeriod))
                .toList();

        ScheduleTimeDensityChartVo vo = new ScheduleTimeDensityChartVo();
        vo.setPlanId(planId);
        vo.setItems(result);
        return vo;
    }

    public ScheduleScoreRadarChartVo getScoreRadar(Long planId) {
        SchedulePlan plan = loadPlan(planId);
        List<ScheduleScoreDetail> details = scheduleScoreService.getScoreDetails(planId);
        if (details.isEmpty()) {
            details = scheduleScoreService.previewScoreDetails(planId);
        }

        Map<String, List<ScheduleScoreDetail>> grouped = details.stream()
                .collect(Collectors.groupingBy(this::mapRadarKey, LinkedHashMap::new, Collectors.toList()));

        List<ScheduleScoreRadarChartVo.Item> result = new ArrayList<>();
        appendRadarItem(result, grouped, "HARD_CONFLICT", "硬性冲突", "硬约束违规越少，图形越完整。");
        appendRadarItem(result, grouped, "TEACHER_BALANCE", "教师均衡", "教师每日负载越均衡，得分越高。");
        appendRadarItem(result, grouped, "ROOM_UTILIZATION", "教室利用", "教室使用越合理，得分越高。");
        appendRadarItem(result, grouped, "CLASS_LOAD", "班级负载", "班级单日负载越均衡，得分越高。");
        appendRadarItem(result, grouped, "TIME_DISTRIBUTION", "时间分布", "课程分布和连续节次越合理，得分越高。");

        if (result.isEmpty()) {
            ScheduleScoreRadarChartVo.Item fallback = new ScheduleScoreRadarChartVo.Item();
            fallback.setName("方案总分");
            fallback.setValue(plan.getTotalScore() == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : plan.getTotalScore().setScale(2, RoundingMode.HALF_UP));
            fallback.setDescription("当前方案缺少评分维度明细，暂时展示总分。");
            result.add(fallback);
        }

        ScheduleScoreRadarChartVo vo = new ScheduleScoreRadarChartVo();
        vo.setPlanId(planId);
        vo.setItems(result);
        return vo;
    }

    private void appendRadarItem(
            List<ScheduleScoreRadarChartVo.Item> result,
            Map<String, List<ScheduleScoreDetail>> grouped,
            String radarKey,
            String name,
            String description
    ) {
        List<ScheduleScoreDetail> items = grouped.get(radarKey);
        if (items == null || items.isEmpty()) {
            return;
        }
        BigDecimal totalWeight = items.stream()
                .map(ScheduleScoreDetail::getMaxScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPenalty = items.stream()
                .map(ScheduleScoreDetail::getScore)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal scoreValue = BigDecimal.valueOf(100);
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            scoreValue = BigDecimal.valueOf(100).subtract(
                    totalPenalty.divide(totalWeight, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            );
        }
        if (scoreValue.compareTo(BigDecimal.ZERO) < 0) {
            scoreValue = BigDecimal.ZERO;
        }
        if (scoreValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            scoreValue = BigDecimal.valueOf(100);
        }

        ScheduleScoreRadarChartVo.Item item = new ScheduleScoreRadarChartVo.Item();
        item.setName(name);
        item.setValue(scoreValue.setScale(2, RoundingMode.HALF_UP));
        item.setDescription(description);
        result.add(item);
    }

    private SchedulePlan loadPlan(Long planId) {
        SchedulePlan plan = schedulePlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        return plan;
    }

    private List<SchedulePlanItem> loadPlanItems(Long planId) {
        loadPlan(planId);
        return schedulePlanItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, planId)
                        .orderByAsc(SchedulePlanItem::getWeekday)
                        .orderByAsc(SchedulePlanItem::getStartPeriod));
    }

    private <T> List<Long> collectIds(List<SchedulePlanItem> items, Function<SchedulePlanItem, Long> idFunc) {
        return items.stream()
                .map(idFunc)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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

    private String roomTypeText(String roomType) {
        RoomType type = RoomType.fromCode(roomType);
        return type == null ? (roomType == null ? "未知类型" : roomType) : type.getLabel();
    }

    private static Integer sumIntegers(Integer left, Integer right) {
        int safeLeft = left == null ? 0 : left;
        int safeRight = right == null ? 0 : right;
        return safeLeft + safeRight;
    }

    private String mapRadarKey(ScheduleScoreDetail detail) {
        return switch (detail.getRuleCode()) {
            case "TEACHER_TIME_CONFLICT", "CLASS_TIME_CONFLICT", "CLASSROOM_TIME_CONFLICT", "TEACHER_UNAVAILABLE", "CLASSROOM_CAPACITY", "CLASSROOM_TYPE_MISMATCH"
                    -> "HARD_CONFLICT";
            case "TEACHER_DAILY_LOAD" -> "TEACHER_BALANCE";
            case "CLASSROOM_UTILIZATION" -> "ROOM_UTILIZATION";
            case "CLASS_DAILY_BALANCE" -> "CLASS_LOAD";
            case "COURSE_DISTRIBUTION", "CONTINUOUS_PERIOD_LIMIT", "MORNING_THEORY_PRIORITY" -> "TIME_DISTRIBUTION";
            default -> detail.getRuleCode();
        };
    }
}
