package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ScheduleRuleConfig;
import com.paike.scheduler.mapper.ScheduleRuleConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ScheduleRuleService {

    private final ScheduleRuleConfigMapper ruleConfigMapper;

    /** 规则值缓存，updateRules / resetToDefault 时清除 */
    private final Map<String, String> ruleCache = new ConcurrentHashMap<>();

    public List<ScheduleRuleConfig> listAll() {
        return ruleConfigMapper.selectList(new LambdaQueryWrapper<ScheduleRuleConfig>()
                .orderByAsc(ScheduleRuleConfig::getId));
    }

    public void updateRules(List<ScheduleRuleConfig> rules) {
        for (ScheduleRuleConfig rule : rules) {
            // 校验每日最大课程数必须大于 0
            if ("TEACHER_MAX_DAILY_SLOTS".equals(rule.getRuleKey()) ||
                "CLASS_MAX_DAILY_SLOTS".equals(rule.getRuleKey())) {
                int value = parseInt(rule.getRuleValue(), rule.getRuleKey());
                if (value <= 0) {
                    throw new BusinessException(rule.getRuleKey().equals("TEACHER_MAX_DAILY_SLOTS")
                            ? "教师每天最大课程数必须大于 0"
                            : "班级每天最大课程数必须大于 0");
                }
            }

            ScheduleRuleConfig existing = ruleConfigMapper.selectOne(new LambdaQueryWrapper<ScheduleRuleConfig>()
                    .eq(ScheduleRuleConfig::getRuleKey, rule.getRuleKey()));
            if (existing != null) {
                existing.setRuleValue(rule.getRuleValue());
                existing.setEnabled(rule.getEnabled());
                existing.setUpdateTime(LocalDateTime.now());
                ruleConfigMapper.updateById(existing);
            }
        }
        ruleCache.clear();
    }

    public void resetToDefault() {
        List<ScheduleRuleConfig> defaults = List.of(
                createRule("TEACHER_MAX_DAILY_SLOTS", "3", "教师每天最多课程大节数", "每位教师每天最多安排的大节数量", 1),
                createRule("CLASS_MAX_DAILY_SLOTS", "4", "班级每天最多课程大节数", "每个班级每天最多安排的大节数量", 1),
                createRule("PRIORITIZE_MORNING", "true", "优先上午排课", "自动排课时优先安排上午时间段", 1),
                createRule("AVOID_FRIDAY_AFTERNOON", "true", "避免周五下午排课", "自动排课时尽量避免安排周五下午课程", 1),
                createRule("ALLOW_SAME_COURSE_SAME_DAY", "false", "允许同一课程同一天重复出现", "同一班级同一课程是否可以在一天内排多次", 1)
        );

        for (ScheduleRuleConfig rule : defaults) {
            ScheduleRuleConfig existing = ruleConfigMapper.selectOne(new LambdaQueryWrapper<ScheduleRuleConfig>()
                    .eq(ScheduleRuleConfig::getRuleKey, rule.getRuleKey()));
            if (existing != null) {
                existing.setRuleValue(rule.getRuleValue());
                existing.setEnabled(rule.getEnabled());
                existing.setUpdateTime(LocalDateTime.now());
                ruleConfigMapper.updateById(existing);
            } else {
                rule.setCreateTime(LocalDateTime.now());
                rule.setUpdateTime(LocalDateTime.now());
                ruleConfigMapper.insert(rule);
            }
        }
        ruleCache.clear();
    }

    private ScheduleRuleConfig createRule(String key, String value, String name, String desc, int enabled) {
        ScheduleRuleConfig rule = new ScheduleRuleConfig();
        rule.setRuleKey(key);
        rule.setRuleValue(value);
        rule.setRuleName(name);
        rule.setDescription(desc);
        rule.setEnabled(enabled);
        return rule;
    }

    public int getIntValue(String ruleKey) {
        String cached = ruleCache.get(ruleKey);
        if (cached != null) return parseInt(cached, ruleKey);
        ScheduleRuleConfig rule = ruleConfigMapper.selectOne(new LambdaQueryWrapper<ScheduleRuleConfig>()
                .eq(ScheduleRuleConfig::getRuleKey, ruleKey));
        if (rule == null) return 0;
        ruleCache.put(ruleKey, rule.getRuleValue());
        return parseInt(rule.getRuleValue(), ruleKey);
    }

    public boolean getBoolValue(String ruleKey) {
        String cached = ruleCache.get(ruleKey);
        if (cached != null) return Boolean.parseBoolean(cached);
        ScheduleRuleConfig rule = ruleConfigMapper.selectOne(new LambdaQueryWrapper<ScheduleRuleConfig>()
                .eq(ScheduleRuleConfig::getRuleKey, ruleKey));
        if (rule == null) return false;
        ruleCache.put(ruleKey, rule.getRuleValue());
        return Boolean.parseBoolean(rule.getRuleValue());
    }

    private int parseInt(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(key + " 的值为空，无法解析为整数");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            throw new BusinessException(key + " 的值 '" + value + "' 不是有效整数");
        }
    }
}
