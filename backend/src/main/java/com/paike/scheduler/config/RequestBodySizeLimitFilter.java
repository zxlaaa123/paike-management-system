package com.paike.scheduler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.response.Result;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 应用层请求体大小兜底。
 *
 * 本项目无文件上传端点，请求体全部为 JSON（@RequestBody）。Spring 的
 * spring.servlet.multipart.max-request-size 只作用于 multipart/表单，
 * server.tomcat.max-http-form-post-size 只作用于 x-www-form-urlencoded，
 * 两者都不拦裸 JSON body，因此用本过滤器设上限，防止超大 body 造成内存压力。
 *
 * 过滤器在 DispatcherServlet 之前执行，GlobalExceptionHandler 接不住这里抛出的异常，
 * 故超限时直接写出统一信封 Result.fail(413, ...)。
 *
 * 有 Content-Length 时在进入过滤链前拒绝；无 Content-Length（如 chunked）时包装输入流，
 * 下游读取超过上限会抛出异常并在这里返回统一 413。
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
        ServletRequest guardedRequest = request;
        if (request instanceof HttpServletRequest httpRequest) {
            if (contentLength < 0) {
                try {
                    guardedRequest = new CachedBodyRequestWrapper(httpRequest,
                            readBodyWithinLimit(httpRequest.getInputStream()));
                } catch (PayloadTooLargeException ex) {
                    if (response instanceof HttpServletResponse httpResponse) {
                        writePayloadTooLarge(httpResponse);
                        return;
                    }
                    throw ex;
                }
            } else {
                guardedRequest = new SizeLimitedRequestWrapper(httpRequest, maxRequestBodySize);
            }
        }
        try {
            chain.doFilter(guardedRequest, response);
        } catch (PayloadTooLargeException ex) {
            if (response instanceof HttpServletResponse httpResponse) {
                writePayloadTooLarge(httpResponse);
                return;
            }
            throw ex;
        }
    }

    private byte[] readBodyWithinLimit(ServletInputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            total += count;
            if (total > maxRequestBodySize) {
                throw new PayloadTooLargeException();
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void writePayloadTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = objectMapper.writeValueAsString(
                Result.fail(413, "请求体过大，最大允许 " + maxRequestBodySize + " 字节"));
        response.getWriter().write(body);
    }

    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            if (encoding == null || encoding.isBlank()) {
                encoding = StandardCharsets.UTF_8.name();
            }
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private CachedBodyServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("Async IO is not supported");
        }

        @Override
        public int read() {
            return delegate.read();
        }
    }

    private static final class SizeLimitedRequestWrapper extends HttpServletRequestWrapper {

        private final long maxBytes;

        private SizeLimitedRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new SizeLimitedServletInputStream(super.getInputStream(), maxBytes);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            if (encoding == null || encoding.isBlank()) {
                encoding = StandardCharsets.UTF_8.name();
            }
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }

    private static final class SizeLimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead;

        private SizeLimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                countBytes(1);
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = delegate.read(b, off, len);
            if (count > 0) {
                countBytes(count);
            }
            return count;
        }

        private void countBytes(int count) throws PayloadTooLargeException {
            bytesRead += count;
            if (bytesRead > maxBytes) {
                throw new PayloadTooLargeException();
            }
        }
    }

    private static final class PayloadTooLargeException extends IOException {
    }
}
