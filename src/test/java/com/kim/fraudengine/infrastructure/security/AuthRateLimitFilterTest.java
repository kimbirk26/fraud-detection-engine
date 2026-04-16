package com.kim.fraudengine.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    private static final AuthRateLimitProperties PROPS =
            new AuthRateLimitProperties(3, 3, false, List.of(), 60, 10_000, 100);

    @Test
    void allows_requests_within_capacity() throws Exception {
        var filter = new AuthRateLimitFilter(PROPS);
        var chain = mock(FilterChain.class);

        for (int i = 0; i < PROPS.capacity(); i++) {
            filter.doFilter(request("10.0.0.1", "POST"), new MockHttpServletResponse(), chain);
        }

        verify(chain, times(PROPS.capacity())).doFilter(any(), any());
    }

    @Test
    void returns_429_when_capacity_exceeded() throws Exception {
        var filter = new AuthRateLimitFilter(PROPS);
        var chain = mock(FilterChain.class);

        for (int i = 0; i < PROPS.capacity(); i++) {
            filter.doFilter(request("10.0.0.2", "POST"), new MockHttpServletResponse(), chain);
        }

        var res = new MockHttpServletResponse();
        filter.doFilter(request("10.0.0.2", "POST"), res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("Too many requests");
        assertThat(res.getHeader("Retry-After")).isNotBlank();
        verify(chain, times(PROPS.capacity())).doFilter(any(), any());
    }

    @Test
    void buckets_are_independent_per_ip() throws Exception {
        var filter = new AuthRateLimitFilter(PROPS);
        var chain = mock(FilterChain.class);

        // Exhaust IP A
        for (int i = 0; i < PROPS.capacity(); i++) {
            filter.doFilter(request("192.168.0.1", "POST"), new MockHttpServletResponse(), chain);
        }

        // IP B should still have a full bucket
        var res = new MockHttpServletResponse();
        filter.doFilter(request("192.168.0.2", "POST"), res, chain);

        assertThat(res.getStatus()).isNotEqualTo(429);
        verify(chain, times(PROPS.capacity() + 1)).doFilter(any(), any());
    }

    @Test
    void ignores_spoofed_x_forwarded_for_when_request_is_not_from_trusted_proxy() throws Exception {
        var filter =
                new AuthRateLimitFilter(
                        new AuthRateLimitProperties(
                                1, 1, true, List.of("10.0.0.0/8"), 60, 10_000, 100));
        var chain = mock(FilterChain.class);

        var firstRequest = request("203.0.113.10", "POST");
        firstRequest.addHeader("X-Forwarded-For", "1.2.3.4");
        filter.doFilter(firstRequest, new MockHttpServletResponse(), chain);

        var req = request("203.0.113.10", "POST");
        req.addHeader("X-Forwarded-For", "5.6.7.8");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void uses_rightmost_untrusted_forwarded_ip_when_request_is_from_trusted_proxy()
            throws Exception {
        var filter =
                new AuthRateLimitFilter(
                        new AuthRateLimitProperties(
                                1, 1, true, List.of("10.0.0.0/8"), 60, 10_000, 100));
        var chain = mock(FilterChain.class);

        var firstRequest = request("10.0.0.1", "POST");
        firstRequest.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.10");
        filter.doFilter(firstRequest, new MockHttpServletResponse(), chain);

        var req = request("10.0.0.1", "POST");
        req.addHeader("X-Forwarded-For", "5.6.7.8, 198.51.100.10");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void non_post_requests_do_not_consume_login_budget() throws Exception {
        var filter =
                new AuthRateLimitFilter(
                        new AuthRateLimitProperties(1, 1, false, List.of(), 60, 10_000, 100));
        var chain = mock(FilterChain.class);

        filter.doFilter(request("10.0.0.3", "OPTIONS"), new MockHttpServletResponse(), chain);

        var loginResponse = new MockHttpServletResponse();
        filter.doFilter(request("10.0.0.3", "POST"), loginResponse, chain);

        assertThat(loginResponse.getStatus()).isNotEqualTo(429);
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void expires_idle_buckets_and_admits_new_clients_after_cleanup() throws Exception {
        var clock = new MutableClock(Instant.parse("2026-04-11T00:00:00Z"));
        var filter =
                new AuthRateLimitFilter(
                        new AuthRateLimitProperties(3, 3, false, List.of(), 1, 1, 1), clock);
        var chain = mock(FilterChain.class);

        filter.doFilter(request("192.0.2.10", "POST"), new MockHttpServletResponse(), chain);

        var storeFullResponse = new MockHttpServletResponse();
        filter.doFilter(request("192.0.2.11", "POST"), storeFullResponse, chain);
        assertThat(storeFullResponse.getStatus()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(2));

        var afterExpiryResponse = new MockHttpServletResponse();
        filter.doFilter(request("192.0.2.11", "POST"), afterExpiryResponse, chain);

        assertThat(afterExpiryResponse.getStatus()).isNotEqualTo(429);
        verify(chain, times(2)).doFilter(any(), any());
    }

    private MockHttpServletRequest request(String remoteAddr, String method) {
        var req = new MockHttpServletRequest(method, "/api/v1/auth/token");
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
