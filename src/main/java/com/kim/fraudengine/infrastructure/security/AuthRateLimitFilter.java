package com.kim.fraudengine.infrastructure.security;

import com.kim.fraudengine.infrastructure.logging.SensitiveLogValueSanitizer;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP token-bucket rate limiter for the authentication endpoint.
 *
 * <p>Each unique client IP gets its own bucket. Requests are allowed up to
 * {@code capacity} tokens; the bucket refills at {@code refillPerMinute}
 * tokens per minute. Once a bucket is empty the request is rejected with
 * HTTP 429.
 *
 * <p>Note: buckets are held in-process memory. For multi-instance deployments
 * consider replacing the map with a distributed store (e.g. Redis via
 * bucket4j-redis) so limits are enforced cluster-wide.
 *
 * <p>This filter is NOT a {@code @Component} — it is registered explicitly
 * via {@link SecurityConfig#authRateLimitFilterRegistration(AuthRateLimitFilter)}
 * to prevent Spring Boot from
 * auto-registering it on all paths in addition to the configured URL pattern.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger securityLog =
            LoggerFactory.getLogger("com.capitec.fraud.security.events");

    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceCleanup = new AtomicInteger();
    private final int capacity;
    private final int refillPerMinute;
    private final boolean trustForwardedHeaders;
    private final List<IpAddressMatcher> trustedProxyMatchers;
    private final Duration entryTtl;
    private final int maxTrackedClients;
    private final int cleanupInterval;
    private final Clock clock;

    public AuthRateLimitFilter(AuthRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthRateLimitFilter(AuthRateLimitProperties properties, Clock clock) {
        this.capacity = properties.capacity();
        this.refillPerMinute = properties.refillPerMinute();
        this.trustForwardedHeaders = properties.trustForwardedHeaders();
        this.trustedProxyMatchers = properties.trustedProxies().stream()
                .map(IpAddressMatcher::new)
                .toList();
        this.entryTtl = Duration.ofMinutes(properties.entryTtlMinutes());
        this.maxTrackedClients = properties.maxTrackedClients();
        this.cleanupInterval = properties.cleanupInterval();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long nowMillis = clock.millis();
        evictExpiredBucketsIfDue(nowMillis);
        String clientIp = resolveClientIp(request);
        Bucket bucket = resolveBucket(clientIp, nowMillis);
        if (bucket == null) {
            securityLog.warn(
                    "event=rate_limit_store_full path={} remote={} trackedClients={}",
                    SensitiveLogValueSanitizer.normalizeForLog(request.getRequestURI()),
                    SensitiveLogValueSanitizer.normalizeForLog(clientIp),
                    buckets.size());
            writeTooManyRequests(response, Math.max(1L, entryTtl.toSeconds()));
            return;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            securityLog.warn("event=rate_limit_exceeded path={} remote={}",
                    SensitiveLogValueSanitizer.normalizeForLog(request.getRequestURI()),
                    SensitiveLogValueSanitizer.normalizeForLog(clientIp));
            writeTooManyRequests(response, retryAfterSeconds(probe));
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refillPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private Bucket resolveBucket(String clientIp, long nowMillis) {
        BucketEntry existing = buckets.get(clientIp);
        if (existing != null) {
            if (!isExpired(existing, nowMillis)) {
                existing.touch(nowMillis);
                return existing.bucket();
            }
            buckets.remove(clientIp, existing);
        }

        evictExpiredBuckets(nowMillis);
        if (buckets.size() >= maxTrackedClients) {
            return null;
        }

        BucketEntry created = new BucketEntry(newBucket(), nowMillis);
        BucketEntry active = buckets.putIfAbsent(clientIp, created);
        BucketEntry bucketEntry = active == null ? created : active;
        bucketEntry.touch(nowMillis);
        return bucketEntry.bucket();
    }

    private void evictExpiredBucketsIfDue(long nowMillis) {
        if (requestsSinceCleanup.incrementAndGet() >= cleanupInterval) {
            requestsSinceCleanup.set(0);
            evictExpiredBuckets(nowMillis);
        }
    }

    private void evictExpiredBuckets(long nowMillis) {
        buckets.entrySet().removeIf(entry -> isExpired(entry.getValue(), nowMillis));
    }

    private boolean isExpired(BucketEntry bucketEntry, long nowMillis) {
        long ttlMillis = entryTtl.toMillis();
        return nowMillis - bucketEntry.lastSeenAtMillis() >= ttlMillis;
    }

    /**
     * Returns the originating IP. Forwarded headers are only trusted when the
     * request itself arrived through a configured trusted proxy. When a trusted
     * proxy appends to an existing X-Forwarded-For chain, we walk from right to
     * left and take the first non-proxy hop so spoofed left-most values do not
     * create fresh buckets.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustForwardedHeaders || !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddr;
        }

        String[] forwardedChain = forwarded.split(",");
        for (int i = forwardedChain.length - 1; i >= 0; i--) {
            String candidate = normalizeForwardedIp(forwardedChain[i]);
            if (candidate == null) {
                continue;
            }
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }

        return remoteAddr;
    }

    private boolean isTrustedProxy(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private String normalizeForwardedIp(String candidate) {
        if (candidate == null) {
            return null;
        }

        String trimmed = candidate.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return null;
        }

        boolean hasIpCharactersOnly = trimmed.chars().allMatch(ch ->
                Character.digit(ch, 16) != -1 || ch == '.' || ch == ':');
        boolean resemblesIpLiteral = trimmed.contains(".") || trimmed.contains(":");
        return hasIpCharactersOnly && resemblesIpLiteral ? trimmed : null;
    }

    private long retryAfterSeconds(ConsumptionProbe probe) {
        long nanosToWait = probe.getNanosToWaitForRefill();
        if (nanosToWait <= 0) {
            return 1;
        }
        return Math.max(1L, (nanosToWait + 999_999_999L) / 1_000_000_000L);
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Too many requests\"}");
    }

    private static final class BucketEntry {
        private final Bucket bucket;
        private volatile long lastSeenAtMillis;

        private BucketEntry(Bucket bucket, long lastSeenAtMillis) {
            this.bucket = bucket;
            this.lastSeenAtMillis = lastSeenAtMillis;
        }

        private Bucket bucket() {
            return bucket;
        }

        private long lastSeenAtMillis() {
            return lastSeenAtMillis;
        }

        private void touch(long nowMillis) {
            this.lastSeenAtMillis = nowMillis;
        }
    }
}
