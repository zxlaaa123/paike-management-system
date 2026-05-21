package com.paike.scheduler.controller;

import com.paike.scheduler.auth.AuthService;
import com.paike.scheduler.auth.dto.LoginRequest;
import com.paike.scheduler.auth.vo.LoginResponse;
import com.paike.scheduler.auth.vo.UserInfoVo;
import com.paike.scheduler.common.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request, resolveClientIp(httpRequest));

        // 设置 httpOnly JWT Cookie（防 XSS 窃取）。secure 由 app.security.cookie-secure 控制：
        // 本地 HTTP 留 false，生产 HTTPS 通过 COOKIE_SECURE=true 切换。
        ResponseCookie jwtCookie = ResponseCookie.from("paike_token", loginResponse.getToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api")
                .maxAge(expirationMs / 1000)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", jwtCookie.toString());

        // 设置可读 CSRF Cookie（前端读取后放入 X-CSRF-Token 请求头）
        String csrfToken = UUID.randomUUID().toString();
        ResponseCookie csrfCookie = ResponseCookie.from("XSRF-TOKEN", csrfToken)
                .httpOnly(false)
                .secure(cookieSecure)
                .path("/")
                .maxAge(expirationMs / 1000)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", csrfCookie.toString());

        return Result.success(loginResponse);
    }

    @GetMapping("/me")
    public Result<UserInfoVo> me() {
        return Result.success(authService.currentUser());
    }

    @PostMapping("/logout")
    public Result<Map<String, Object>> logout(HttpServletResponse response) {
        ResponseCookie clearJwt = ResponseCookie.from("paike_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", clearJwt.toString());

        ResponseCookie clearCsrf = ResponseCookie.from("XSRF-TOKEN", "")
                .httpOnly(false)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", clearCsrf.toString());

        return Result.success(Map.of("success", true));
    }

    /**
     * 从请求头里解析真实客户端 IP，用于登录限流的 IP 维度（A1）。
     * 优先级：X-Forwarded-For 首项 > X-Real-IP > remoteAddr。
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
