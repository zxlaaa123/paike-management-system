package com.paike.scheduler.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SysUser;
import com.paike.scheduler.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtService jwtService;
    private final SysUserMapper sysUserMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 仅对状态变更请求（POST/PUT/DELETE）做 CSRF 校验
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            String csrfHeader = request.getHeader("X-CSRF-Token");
            String csrfCookie = getCookieValue(request, "XSRF-TOKEN");
            // CSRF Cookie 存在时要求请求头与之匹配
            if (csrfCookie != null && !csrfCookie.isBlank()
                    && (csrfHeader == null || !csrfCookie.equals(csrfHeader))) {
                throw new BusinessException(403, "CSRF 校验失败");
            }
        }

        // 优先从 httpOnly Cookie 读取 token，其次从 Authorization 头
        String token = getCookieValue(request, "paike_token");
        if (token == null || token.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                token = authHeader.substring(BEARER_PREFIX.length()).trim();
            }
        }

        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "未登录或登录已过期");
        }

        try {
            Claims claims = jwtService.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());
            SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId));
            if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
                throw new BusinessException(401, "未登录或登录已过期");
            }
            AuthUserContext.set(user);
            return true;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
    }

    private static String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthUserContext.clear();
    }
}
