package com.paike.scheduler.service.scheduling;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.entity.TeacherUnavailableTime;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.TeacherUnavailableTimeMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.ScheduleRuleService;
import com.paike.scheduler.service.ScheduleRuleWeightService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 调度引擎共享的数据加载层。AutoScheduleService 与 V3ScheduleGenerateService 之前各自
 * 重复加载时间段/教室/教师禁排/课程/班级，这里统一出口，确保两边读取口径一致。
 *
 * 简化代价：courseMap / classMap 改为全量加载 (deleted=0)，不再按任务 id 过滤。
 * 当前 paike 项目 course/class 总量在百级别，全量加载内存可控；map.get(id) 取值
 * 对正确性无影响，仅多查未使用的行。若后续遇到万级数据量再加 task 参数化重载。
 */
@Component
@RequiredArgsConstructor
public class SchedulingReferenceLoader {

    private final TimeSlotMapper timeSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final TeacherUnavailableTimeMapper unavailableTimeMapper;
    private final CourseMapper courseMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ScheduleRuleService ruleService;
    private final ScheduleRuleWeightService ruleWeightService;

    /** AutoScheduleService 入口：不需要权重。 */
    public SchedulingReferenceData loadForAutoSchedule() {
        return loadCommon(Map.of());
    }

    /** V3ScheduleGenerateService 入口：按学期 + 策略加载权重。 */
    public SchedulingReferenceData loadForV3Generate(Long semesterId, String strategyType) {
        return loadCommon(loadWeights(semesterId, strategyType));
    }

    private SchedulingReferenceData loadCommon(Map<String, BigDecimal> weightMap) {
        boolean prioritizeMorning = ruleService.getBoolValue("PRIORITIZE_MORNING");
        boolean avoidFridayAfternoon = ruleService.getBoolValue("AVOID_FRIDAY_AFTERNOON");

        List<TimeSlot> rawSlots = timeSlotMapper.selectList(
                new LambdaQueryWrapper<TimeSlot>().orderByAsc(TimeSlot::getSortOrder));
        List<TimeSlot> sorted = SchedulingSupport.sortTimeSlots(rawSlots, prioritizeMorning, avoidFridayAfternoon);

        List<Classroom> classrooms = classroomMapper.selectList(
                new LambdaQueryWrapper<Classroom>()
                        .eq(Classroom::getStatus, 1)
                        .eq(Classroom::getDeleted, 0));

        List<TeacherUnavailableTime> unavailables = unavailableTimeMapper.selectList(
                new LambdaQueryWrapper<TeacherUnavailableTime>()
                        .eq(TeacherUnavailableTime::getStatus, 1)
                        .eq(TeacherUnavailableTime::getDeleted, 0));

        Map<Long, Course> courseMap = courseMapper.selectList(
                        new LambdaQueryWrapper<Course>().eq(Course::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        Map<Long, ClassInfo> classMap = classInfoMapper.selectList(
                        new LambdaQueryWrapper<ClassInfo>().eq(ClassInfo::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(ClassInfo::getId, c -> c, (a, b) -> a));

        return new SchedulingReferenceData(
                sorted,
                SchedulingSupport.slotIdsByDay(sorted),
                classrooms,
                SchedulingSupport.toUnavailableKeySet(unavailables),
                SchedulingSupport.toUnavailableCountByTeacher(unavailables),
                courseMap,
                classMap,
                weightMap);
    }

    private Map<String, BigDecimal> loadWeights(Long semesterId, String strategyType) {
        List<ScheduleRuleWeight> rules = ruleWeightService.list(semesterId, strategyType, null);
        if (rules.isEmpty()) {
            ruleWeightService.initDefaultRules(semesterId, strategyType);
            rules = ruleWeightService.list(semesterId, strategyType, null);
        }
        return rules.stream()
                .filter(rule -> rule.getEnabled() != null && rule.getEnabled() == 1)
                .collect(Collectors.toMap(
                        ScheduleRuleWeight::getRuleCode,
                        rule -> rule.getWeight() != null ? rule.getWeight() : BigDecimal.ZERO,
                        (a, b) -> a));
    }
}
