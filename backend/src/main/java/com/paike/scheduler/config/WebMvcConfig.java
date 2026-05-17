package com.paike.scheduler.config;

import com.paike.scheduler.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * API 版本策略说明：
 * - 基础 CRUD 接口使用 /api/ 前缀（如 /api/teachers, /api/schedules）
 * - V3 高级排课功能使用 /v3/ 前缀（如 /v3/schedule-plans）
 * - 后续新功能统一使用版本化前缀（/v4/、/v5/...），旧模块不建议整体迁移
 * - 当前不一致的历史遗留问题，在后续大版本重构时统一
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/login",
                "/api/health",
                "/error"
            );
    }
}
