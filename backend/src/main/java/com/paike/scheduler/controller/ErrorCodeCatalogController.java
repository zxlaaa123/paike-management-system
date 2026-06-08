package com.paike.scheduler.controller;

import com.paike.scheduler.common.response.Result;
import com.paike.scheduler.service.ErrorCodeCatalogService;
import com.paike.scheduler.service.vo.ErrorCodeVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v6/error-codes")
@RequiredArgsConstructor
public class ErrorCodeCatalogController {

    private final ErrorCodeCatalogService errorCodeCatalogService;

    @GetMapping
    public Result<List<ErrorCodeVo>> list(@RequestParam(required = false) String category) {
        return Result.success(errorCodeCatalogService.list(category));
    }

    @GetMapping("/{code}")
    public Result<ErrorCodeVo> getByCode(@PathVariable String code) {
        return Result.success(errorCodeCatalogService.getByCode(code));
    }
}
