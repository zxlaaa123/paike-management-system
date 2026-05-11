package com.paike.scheduler.controller;

import com.paike.scheduler.auth.AuthService;
import com.paike.scheduler.auth.dto.LoginRequest;
import com.paike.scheduler.auth.vo.LoginResponse;
import com.paike.scheduler.auth.vo.UserInfoVo;
import com.paike.scheduler.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<UserInfoVo> me() {
        return Result.success(authService.currentUser());
    }

    @PostMapping("/logout")
    public Result<Map<String, Object>> logout() {
        return Result.success(Map.of("success", true));
    }
}
