package com.kim.fraudengine.infrastructure.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_echoesSafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.CORRELATION_HEADER, "TRACE-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> correlationIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                correlationIdInChain.set(MDC.get(RequestCorrelationFilter.CORRELATION_MDC_KEY)));

        assertThat(correlationIdInChain.get()).isEqualTo("TRACE-1234");
        assertThat(response.getHeader(RequestCorrelationFilter.CORRELATION_HEADER)).isEqualTo("TRACE-1234");
        assertThat(MDC.get(RequestCorrelationFilter.CORRELATION_MDC_KEY)).isNull();
    }

    @Test
    void doFilter_replacesUnsafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.CORRELATION_HEADER, "bad value\r\nX-Test: injected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> correlationIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                correlationIdInChain.set(MDC.get(RequestCorrelationFilter.CORRELATION_MDC_KEY)));

        assertThat(correlationIdInChain.get()).matches("[A-Z0-9-]{8,64}");
        assertThat(correlationIdInChain.get()).isNotEqualTo("bad value\r\nX-Test: injected");
        assertThat(response.getHeader(RequestCorrelationFilter.CORRELATION_HEADER))
                .isEqualTo(correlationIdInChain.get());
        assertThat(MDC.get(RequestCorrelationFilter.CORRELATION_MDC_KEY)).isNull();
    }
}
