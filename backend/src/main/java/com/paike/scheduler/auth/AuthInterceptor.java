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
        // 先解析 token 来源：Cookie 路径才会被浏览器自动携带，CSRF 风险才存在；
        // Authorization Bearer 是 API 客户端主动设置，浏览器不会跨站自动加，CSRF 不适用。
        String cookieToken = getCookieValue(request, "paike_token");
        boolean cookieAuth = cookieToken != null && !cookieToken.isBlank();

        // 仅对 cookie 认证的状态变更请求强制 CSRF（POST/PUT/DELETE/PATCH）
        String method = request.getMethod();
        boolean stateChanging = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
        if (cookieAuth && stateChanging) {
            String csrfHeader = request.getHeader("X-CSRF-Token");
            String csrfCookie = getCookieValue(request, "XSRF-TOKEN");
            // 强制要求双 token 模式：cookie 与 header 都必须存在且相等，缺一就拒绝。
            // 旧逻辑"cookie 存在才校验"在攻击者清空 XSRF-TOKEN 时会被绕过。
            if (csrfCookie == null || csrfCookie.isBlank()
                    || csrfHeader == null || csrfHeader.isBlank()
                    || !csrfCookie.equals(csrfHeader)) {
                throw new BusinessException(403, "CSRF 校验失败");
            }
        }

        // 选定最终 token：cookie 优先，再退到 Bearer
        String token = cookieToken;
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
