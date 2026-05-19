package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.V5CandidatePositionService;
import com.paike.scheduler.service.dto.V5CandidatePositionGenerateRequest;
import com.paike.scheduler.service.vo.V5CandidatePositionResultVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v5/candidate-positions")
@RequiredArgsConstructor
public class V5CandidatePositionController {

    private final V5CandidatePositionService candidatePositionService;

    @PostMapping("/generate")
    public Result<V5CandidatePositionResultVo> generate(@RequestBody V5CandidatePositionGenerateRequest request) {
        return Result.success(candidatePositionService.generate(request));
    }
}

