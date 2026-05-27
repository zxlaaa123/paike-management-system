package com.paike.scheduler.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityHeadersFilterTest {

    @Test
    void doFilter_setsContentSecurityPolicy() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (request, servletResponse) -> {
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/api/health"), response, chain);

        assertEquals("default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'",
                response.getHeader("Content-Security-Policy"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }
}
