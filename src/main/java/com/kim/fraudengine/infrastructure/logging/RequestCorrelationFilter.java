package com.kim.fraudengine.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_MDC_KEY = "correlationId";

    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);
        MDC.put(CORRELATION_MDC_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_MDC_KEY);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String existing = request.getHeader(CORRELATION_HEADER);
        if (existing != null) {
            String trimmed = existing.trim();
            if (SAFE_CORRELATION_ID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
