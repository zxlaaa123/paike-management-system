package com.paike.scheduler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestBodySizeLimitFilterTest {

    private static final long MAX = 1024;

    private final RequestBodySizeLimitFilter filter =
            new RequestBodySizeLimitFilter(new ObjectMapper(), MAX);

    @Test
    void doFilter_rejectsOversizedBodyWith413() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/schedules");
        request.setContent(new byte[(int) MAX + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":413"),
                "响应体应为统一信封且 code=413，实际：" + response.getContentAsString());
        assertFalse(chainCalled.get(), "超限请求不应进入后续过滤链");
    }

    @Test
    void doFilter_passesBodyWithinLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/schedules");
        request.setContent(new byte[(int) MAX]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(chainCalled.get(), "阈值内请求应放行");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    void doFilter_passesRequestWithoutContentLength() throws Exception {
        // GET 等无请求体的请求 Content-Length 为 -1，应直接放行
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(chainCalled.get(), "无 Content-Length 的请求应放行");
    }

    @Test
    void doFilter_rejectsChunkedBodyWhenReadBeyondLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/schedules") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[(int) MAX + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(chainCalled.get(), "无 Content-Length 的超限请求不应进入后续过滤链");
        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":413"));
    }

    @Test
    void doFilter_passesChunkedBodyWithinLimitAndKeepsBodyReadable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/schedules") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        byte[] body = "{\"ok\":true}".getBytes();
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean bodyReadable = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> bodyReadable.set(req.getInputStream().readAllBytes().length == body.length);

        filter.doFilter(request, response, chain);

        assertTrue(bodyReadable.get(), "无 Content-Length 的阈值内请求应继续向后传递原始 body");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }
}
