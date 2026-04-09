package com.kim.fraudengine.infrastructure.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a correlation ID to every inbound request and stores it in MDC.
 * Reads X-Correlation-ID from the request header; generates a short random ID
 * if none is present. Cleared from MDC after the request completes.
 */
public class RequestCorrelationFilter implements Filter {

    private static final String HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            String correlationId = null;
            if (request instanceof HttpServletRequest httpRequest) {
                correlationId = httpRequest.getHeader(HEADER);
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
            MDC.put(MDC_KEY, correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
