package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchedulePlanService {

    private final SchedulePlanMapper planMapper;
    private final SchedulePlanItemMapper planItemMapper;

    public Page<SchedulePlan> list(Long semesterId, String status, String strategyType, String keyword, int page, int size) {
        LambdaQueryWrapper<SchedulePlan> wrapper = new LambdaQueryWrapper<SchedulePlan>()
                .eq(SchedulePlan::getSemesterId, semesterId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SchedulePlan::getStatus, status);
        }
        if (strategyType != null && !strategyType.isBlank()) {
            wrapper.eq(SchedulePlan::getStrategyType, strategyType);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SchedulePlan::getName, keyword);
        }
        wrapper.orderByDesc(SchedulePlan::getCreatedAt);
        return planMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public SchedulePlan getById(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        return plan;
    }

    public List<SchedulePlanItem> getPlanItems(Long planId) {
        return planItemMapper.selectList(
                new LambdaQueryWrapper<SchedulePlanItem>()
                        .eq(SchedulePlanItem::getPlanId, planId)
                        .orderByAsc(SchedulePlanItem::getWeekday)
                        .orderByAsc(SchedulePlanItem::getStartPeriod));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        if (!"DRAFT".equals(plan.getStatus())) {
            throw new BusinessException("只能删除草稿方案");
        }
        // 先删除方案明细
        planItemMapper.delete(new LambdaQueryWrapper<SchedulePlanItem>().eq(SchedulePlanItem::getPlanId, id));
        // 再删除方案
        planMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void abandon(Long id) {
        SchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("排课方案不存在");
        }
        plan.setStatus("ABANDONED");
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
    }
}
