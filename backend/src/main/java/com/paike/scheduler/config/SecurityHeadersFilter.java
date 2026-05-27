package com.paike.scheduler.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * The project uses a custom AuthInterceptor and does not include starter-security,
 * so these headers are applied here instead of Spring Security HeadersConfigurer.
 */
@Component
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("Referrer-Policy", "same-origin");
            httpResponse.setHeader("Content-Security-Policy",
                    "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'");

            if (request instanceof HttpServletRequest httpRequest && httpRequest.isSecure()) {
                httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
        }

        chain.doFilter(request, response);
    }
}
