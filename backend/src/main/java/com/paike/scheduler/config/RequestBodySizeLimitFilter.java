package com.paike.scheduler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.response.Result;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 应用层请求体大小兜底。
 *
 * 本项目无文件上传端点，请求体全部为 JSON（@RequestBody）。Spring 的
 * spring.servlet.multipart.max-request-size 只作用于 multipart/表单，
 * server.tomcat.max-http-form-post-size 只作用于 x-www-form-urlencoded，
 * 两者都不拦裸 JSON body，因此用本过滤器按 Content-Length 设上限，防止超大 body 造成内存压力。
 *
 * 过滤器在 DispatcherServlet 之前执行，GlobalExceptionHandler 接不住这里抛出的异常，
 * 故超限时直接写出统一信封 Result.fail(413, ...)。
 *
 * 局限：仅按 Content-Length 判定；显式 chunked（无 Content-Length）请求会放行，
 * 该路径由外层 nginx client_max_body_size 兜底。详见 20260603_N43_请求体大小限制调查.md。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodySizeLimitFilter implements Filter {

    private final ObjectMapper objectMapper;
    private final long maxRequestBodySize;

    public RequestBodySizeLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.max-request-body-size:2097152}") long maxRequestBodySize) {
        this.objectMapper = objectMapper;
        this.maxRequestBodySize = maxRequestBodySize;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxRequestBodySize && response instanceof HttpServletResponse httpResponse) {
            writePayloadTooLarge(httpResponse);
            return;
        }
        chain.doFilter(request, response);
    }

    private void writePayloadTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = objectMapper.writeValueAsString(
                Result.fail(413, "请求体过大，最大允许 " + maxRequestBodySize + " 字节"));
        response.getWriter().write(body);
    }
}
