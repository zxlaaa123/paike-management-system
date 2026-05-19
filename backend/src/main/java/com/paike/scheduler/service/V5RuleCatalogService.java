package com.paike.scheduler.service;

import com.paike.scheduler.common.enums.V5RuleLayerType;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.service.vo.V5RuleMetaVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class V5RuleCatalogService {

    private final ScheduleRuleWeightService scheduleRuleWeightService;

    public List<V5RuleMetaVo> listRules(Long semesterId, String strategyType) {
        Map<String, ScheduleRuleWeight> existing = new HashMap<>();
        if (semesterId != null && strategyType != null) {
            for (ScheduleRuleWeight rule : scheduleRuleWeightService.list(semesterId, strategyType, null)) {
                existing.put(rule.getRuleCode(), rule);
            }
        }

        List<V5RuleMetaVo> result = new ArrayList<>();
        addHardRules(result, existing);
        addSoftRules(result, existing);
        addPreferenceRules(result, existing);
        addRepairRules(result);
        result.sort(Comparator.comparing(V5RuleMetaVo::getRuleType).thenComparing(V5RuleMetaVo::getRuleCode));
        return result;
    }

    public boolean isHardRuleCode(String code) {
        return code != null && switch (code) {
            case "TEACHER_TIME_CONFLICT",
                 "CLASS_TIME_CONFLICT",
                 "CLASSROOM_TIME_CONFLICT",
                 "TEACHER_UNAVAILABLE",
                 "CLASSROOM_CAPACITY",
                 "CLASSROOM_TYPE_MISMATCH" -> true;
            default -> false;
        };
    }

    private void addHardRules(List<V5RuleMetaVo> result, Map<String, ScheduleRuleWeight> existing) {
        result.add(meta("TEACHER_TIME_CONFLICT", "教师时间冲突", V5RuleLayerType.HARD, "同一教师同一时段不可重复排课", existing, true, BigDecimal.valueOf(100)));
        result.add(meta("CLASS_TIME_CONFLICT", "班级时间冲突", V5RuleLayerType.HARD, "同一班级同一时段不可重复排课", existing, true, BigDecimal.valueOf(100)));
        result.add(meta("CLASSROOM_TIME_CONFLICT", "教室时间冲突", V5RuleLayerType.HARD, "同一教室同一时段不可重复排课", existing, true, BigDecimal.valueOf(100)));
        result.add(meta("TEACHER_UNAVAILABLE", "教师禁排", V5RuleLayerType.HARD, "命中教师禁排时间时不可排课", existing, true, BigDecimal.valueOf(90)));
        result.add(meta("CLASSROOM_CAPACITY", "教室容量", V5RuleLayerType.HARD, "班级人数不可超过教室容量", existing, true, BigDecimal.valueOf(80)));
        result.add(meta("CLASSROOM_TYPE_MISMATCH", "教室类型", V5RuleLayerType.HARD, "课程类型应匹配教室类型", existing, true, BigDecimal.valueOf(80)));
    }

    private void addSoftRules(List<V5RuleMetaVo> result, Map<String, ScheduleRuleWeight> existing) {
        result.add(meta("TEACHER_DAILY_LOAD", "教师日负载", V5RuleLayerType.SOFT, "教师每日课时应尽量均衡", existing, false, BigDecimal.valueOf(30)));
        result.add(meta("CLASS_DAILY_BALANCE", "班级日负载", V5RuleLayerType.SOFT, "班级每日课时应尽量均衡", existing, false, BigDecimal.valueOf(30)));
        result.add(meta("COURSE_DISTRIBUTION", "课程分布", V5RuleLayerType.SOFT, "同课程同日不宜过于集中", existing, false, BigDecimal.valueOf(25)));
        result.add(meta("CONTINUOUS_PERIOD_LIMIT", "连续上课", V5RuleLayerType.SOFT, "连续上课过多会降低质量", existing, false, BigDecimal.valueOf(25)));
        result.add(meta("CLASSROOM_UTILIZATION", "教室利用率", V5RuleLayerType.SOFT, "教室使用应平衡且合理", existing, false, BigDecimal.valueOf(20)));
        result.add(meta("TIME_DISTRIBUTION", "时间段分布", V5RuleLayerType.SOFT, "课程时间段分布应尽量均衡", existing, false, BigDecimal.valueOf(20)));
    }

    private void addPreferenceRules(List<V5RuleMetaVo> result, Map<String, ScheduleRuleWeight> existing) {
        result.add(meta("MORNING_THEORY_PRIORITY", "上午优先", V5RuleLayerType.PREFERENCE, "理论课优先安排上午", existing, false, BigDecimal.valueOf(15)));
        result.add(meta("EXPERIMENT_LAB_PREFERENCE", "实验课优先实验室", V5RuleLayerType.PREFERENCE, "实验课优先安排在实验室资源", existing, false, BigDecimal.valueOf(10)));
        result.add(meta("PE_AVOID_NIGHT", "体育课避免晚间", V5RuleLayerType.PREFERENCE, "体育课尽量避免晚间时段", existing, false, BigDecimal.valueOf(10)));
        result.add(meta("TEACHER_SLOT_PREFERENCE", "教师偏好时段", V5RuleLayerType.PREFERENCE, "尽量贴近教师历史/偏好授课时段", existing, false, BigDecimal.valueOf(8)));
    }

    private void addRepairRules(List<V5RuleMetaVo> result) {
        result.add(meta("LOCKED_ITEM_IMMUTABLE", "锁定课程不可移动", V5RuleLayerType.REPAIR, "锁定课程必须保持原安排", Map.of(), true, BigDecimal.ZERO));
        result.add(meta("REPAIR_SCOPE_LIMIT", "只修复指定范围", V5RuleLayerType.REPAIR, "仅允许处理指定范围的课程", Map.of(), true, BigDecimal.ZERO));
        result.add(meta("SIMULATION_ONLY", "仅生成试算方案", V5RuleLayerType.REPAIR, "修复过程仅输出试算方案", Map.of(), true, BigDecimal.ZERO));
        result.add(meta("NO_DIRECT_FORMAL_OVERWRITE", "不直接覆盖正式课表", V5RuleLayerType.REPAIR, "修复阶段不可直接改正式课表", Map.of(), true, BigDecimal.ZERO));
    }

    private V5RuleMetaVo meta(
            String code,
            String name,
            V5RuleLayerType type,
            String description,
            Map<String, ScheduleRuleWeight> existing,
            boolean nonDisableable,
            BigDecimal defaultWeight
    ) {
        ScheduleRuleWeight weight = existing.get(code);
        V5RuleMetaVo vo = new V5RuleMetaVo();
        vo.setRuleCode(code);
        vo.setRuleName(name);
        vo.setRuleType(type.getCode());
        vo.setDescription(description);
        vo.setEnabled(weight == null || weight.getEnabled() == null ? Boolean.TRUE : weight.getEnabled() == 1);
        vo.setWeight(weight == null || weight.getWeight() == null ? defaultWeight : weight.getWeight());
        vo.setNonDisableable(nonDisableable);
        if (nonDisableable) {
            vo.setEnabled(true);
        }
        return vo;
    }
}

