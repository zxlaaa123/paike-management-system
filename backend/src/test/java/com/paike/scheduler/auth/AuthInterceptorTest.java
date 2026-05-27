package com.paike.scheduler.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SysUser;
import com.paike.scheduler.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private JwtService jwtService;
    private SysUserMapper sysUserMapper;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        sysUserMapper = mock(SysUserMapper.class);
        interceptor = new AuthInterceptor(jwtService, sysUserMapper);
    }

    @AfterEach
    void tearDown() {
        AuthUserContext.clear();
    }

    @Test
    void preHandle_rejectsNonAdminStateChangingApiRequest() {
        mockTokenUser(user("USER"));
        MockHttpServletRequest request = bearerRequest("POST", "/api/v4/schedule-reports/plans/1/generate");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(403, ex.getCode());
    }

    @Test
    void preHandle_allowsAdminStateChangingApiRequest() {
        mockTokenUser(user("ADMIN"));
        MockHttpServletRequest request = bearerRequest("POST", "/api/v4/schedule-reports/plans/1/generate");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals("ADMIN", AuthUserContext.get().getRole());
    }

    @Test
    void preHandle_allowsNonAdminReadAndLogoutRequests() {
        mockTokenUser(user("USER"));
        MockHttpServletRequest readRequest = bearerRequest("GET", "/api/v4/schedule-reports/plans/1");
        assertTrue(interceptor.preHandle(readRequest, new MockHttpServletResponse(), new Object()));
        interceptor.afterCompletion(readRequest, new MockHttpServletResponse(), new Object(), null);

        mockTokenUser(user("USER"));
        MockHttpServletRequest logoutRequest = bearerRequest("POST", "/api/auth/logout");
        assertTrue(interceptor.preHandle(logoutRequest, new MockHttpServletResponse(), new Object()));
    }

    @SuppressWarnings("unchecked")
    private void mockTokenUser(SysUser user) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(user.getId()));
        when(jwtService.parseToken("token")).thenReturn(claims);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
    }

    private MockHttpServletRequest bearerRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer token");
        return request;
    }

    private SysUser user(String role) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("user");
        user.setRole(role);
        user.setStatus(1);
        return user;
    }
}
