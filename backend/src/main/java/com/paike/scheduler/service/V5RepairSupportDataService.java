package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.entity.ScheduleCandidatePosition;
import com.paike.scheduler.entity.ScheduleOptimizationCompare;
import com.paike.scheduler.entity.ScheduleRepairSuggestion;
import com.paike.scheduler.mapper.ScheduleCandidatePositionMapper;
import com.paike.scheduler.mapper.ScheduleOptimizationCompareMapper;
import com.paike.scheduler.mapper.ScheduleRepairSuggestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class V5RepairSupportDataService {

    private final ScheduleRepairSuggestionMapper suggestionMapper;
    private final ScheduleCandidatePositionMapper candidatePositionMapper;
    private final ScheduleOptimizationCompareMapper optimizationCompareMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long createSuggestion(ScheduleRepairSuggestion suggestion) {
        suggestionMapper.insert(suggestion);
        return suggestion.getId();
    }

    public List<ScheduleRepairSuggestion> listSuggestionsByTask(Long repairTaskId) {
        return suggestionMapper.selectList(new LambdaQueryWrapper<ScheduleRepairSuggestion>()
                .eq(ScheduleRepairSuggestion::getRepairTaskId, repairTaskId)
                .orderByDesc(ScheduleRepairSuggestion::getCreatedAt)
                .orderByDesc(ScheduleRepairSuggestion::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createCandidatePosition(ScheduleCandidatePosition candidatePosition) {
        candidatePositionMapper.insert(candidatePosition);
        return candidatePosition.getId();
    }

    public List<ScheduleCandidatePosition> listCandidatesBySuggestion(Long suggestionId) {
        return candidatePositionMapper.selectList(new LambdaQueryWrapper<ScheduleCandidatePosition>()
                .eq(ScheduleCandidatePosition::getSuggestionId, suggestionId)
                .orderByAsc(ScheduleCandidatePosition::getRankNo)
                .orderByDesc(ScheduleCandidatePosition::getCandidateScore));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createOptimizationCompare(ScheduleOptimizationCompare compare) {
        optimizationCompareMapper.insert(compare);
        return compare.getId();
    }

    public List<ScheduleOptimizationCompare> listComparesByTask(Long repairTaskId) {
        return optimizationCompareMapper.selectList(new LambdaQueryWrapper<ScheduleOptimizationCompare>()
                .eq(ScheduleOptimizationCompare::getRepairTaskId, repairTaskId)
                .orderByDesc(ScheduleOptimizationCompare::getCreatedAt)
                .orderByDesc(ScheduleOptimizationCompare::getId));
    }
}

