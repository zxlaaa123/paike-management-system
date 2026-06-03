package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.controller.vo.RescoreResultVo;
import com.paike.scheduler.controller.vo.ScoreSummaryVo;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.ScheduleScoreDetail;
import com.paike.scheduler.service.SchedulePlanService;
import com.paike.scheduler.service.ScheduleScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v3/schedule-plans")
@RequiredArgsConstructor
public class ScheduleScoreController {

    private final ScheduleScoreService scoreService;
    private final SchedulePlanService planService;

    @GetMapping("/{planId}/score-details")
    public Result<List<ScheduleScoreDetail>> getScoreDetails(@PathVariable Long planId) {
        return Result.success(scoreService.getScoreDetails(planId));
    }

    @GetMapping("/{planId}/score-summary")
    public Result<ScoreSummaryVo> getScoreSummary(@PathVariable Long planId) {
        BigDecimal totalScore = scoreService.getScoreSummary(planId);
        List<ScheduleScoreDetail> details = scoreService.getScoreDetails(planId);
        int hardViolation = 0;
        int softViolation = 0;
        for (ScheduleScoreDetail d : details) {
            if (d.getScore() != null && d.getScore().compareTo(BigDecimal.ZERO) < 0) {
                if ("HARD".equals(d.getRuleType())) {
                    hardViolation++;
                } else {
                    softViolation++;
                }
            }
        }
        return Result.success(new ScoreSummaryVo(
                planId, totalScore, hardViolation, softViolation, getScoreLevel(totalScore)));
    }

    @PostMapping("/{planId}/rescore")
    public Result<RescoreResultVo> rescore(@PathVariable Long planId) {
        SchedulePlan plan = planService.getById(planId);
        planService.refreshPlanConflictState(planId);
        scoreService.rescore(plan);
        SchedulePlan refreshed = planService.getById(planId);
        BigDecimal totalScore = refreshed.getTotalScore();
        return Result.success(new RescoreResultVo(
                planId, totalScore, refreshed.getConflictCount(), getScoreLevel(totalScore)));
    }

    private String getScoreLevel(BigDecimal score) {
        if (score == null) return "未评分";
        int s = score.intValue();
        if (s >= 90) return "优秀";
        if (s >= 80) return "良好";
        if (s >= 70) return "一般";
        if (s >= 60) return "较差";
        return "不推荐";
    }
}
