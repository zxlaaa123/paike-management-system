package com.paike.scheduler.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthControllerClientIpTest {

    @Test
    void resolveClientIp_ignoresForwardedHeadersByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.2");
        request.addHeader("X-Real-IP", "203.0.113.2");

        String clientIp = AuthController.resolveClientIp(request, false);

        assertEquals("10.0.0.10", clientIp);
    }

    @Test
    void resolveClientIp_usesForwardedHeadersOnlyWhenTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.2");

        String clientIp = AuthController.resolveClientIp(request, true);

        assertEquals("203.0.113.1", clientIp);
    }
}
