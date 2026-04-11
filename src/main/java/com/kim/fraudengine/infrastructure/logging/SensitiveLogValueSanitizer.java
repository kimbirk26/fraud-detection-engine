package com.kim.fraudengine.infrastructure.logging;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Centralizes masking and normalization for values written to application and
 * audit logs. The goal is to keep logs useful for correlation while avoiding
 * exposure of raw user and customer identifiers.
 */
public final class SensitiveLogValueSanitizer {

    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Pattern IDENTIFIER_SEGMENT = Pattern.compile(
            "^(?i)(?:[a-z]+[_-])?[a-z]{2,}\\d{2,}[a-z0-9_-]*$");

    private static final String REDACTED = "[REDACTED]";

    private SensitiveLogValueSanitizer() {
    }

    public static String sanitizeDetail(String key, Object value) {
        if (value == null) {
            return null;
        }

        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        String normalizedValue = normalizeForLog(Objects.toString(value, ""));

        if (normalizedValue.isEmpty()) {
            return normalizedValue;
        }
        if (isTokenKey(normalizedKey)) {
            return REDACTED;
        }
        if (isPathKey(normalizedKey)) {
            return sanitizePath(normalizedValue);
        }
        if (isIpKey(normalizedKey)) {
            return maskIp(normalizedValue);
        }
        if (isIdentityKey(normalizedKey)) {
            return maskIdentifier(normalizedValue);
        }
        return normalizedValue;
    }

    public static String maskUsername(String username) {
        return maskIdentifier(username);
    }

    public static String maskCustomerId(String customerId) {
        return maskIdentifier(customerId);
    }

    public static String maskPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            return "anonymous";
        }
        if ("anonymous".equalsIgnoreCase(principal)) {
            return "anonymous";
        }
        return maskIdentifier(principal);
    }

    public static String maskIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "unknown";
        }

        String normalized = normalizeForLog(ipAddress);
        String[] ipv4Segments = normalized.split("\\.");
        if (ipv4Segments.length == 4) {
            return ipv4Segments[0] + "." + ipv4Segments[1] + ".x.x";
        }

        String[] ipv6Segments = normalized.split(":");
        if (ipv6Segments.length >= 2) {
            return ipv6Segments[0] + ":" + ipv6Segments[1] + ":****:****";
        }

        return maskIdentifier(normalized);
    }

    public static String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            return "unknown";
        }

        return Arrays.stream(normalizeForLog(path).split("/"))
                .map(SensitiveLogValueSanitizer::sanitizePathSegment)
                .collect(Collectors.joining("/"));
    }

    static String maskIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return REDACTED;
        }

        String normalized = normalizeForLog(identifier);
        if (normalized.length() <= 2) {
            return "**";
        }
        if (normalized.length() <= 6) {
            return normalized.charAt(0) + "***" + normalized.charAt(normalized.length() - 1);
        }
        return normalized.substring(0, 2) + "***" + normalized.substring(normalized.length() - 2);
    }

    static String normalizeForLog(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) {
            if (Character.isISOControl(ch)) {
                builder.append('_');
            } else if (Character.isWhitespace(ch)) {
                builder.append(' ');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString().trim().replace(' ', '_');
    }

    private static String sanitizePathSegment(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        if (UUID_SEGMENT.matcher(segment).matches() || IDENTIFIER_SEGMENT.matcher(segment).matches()) {
            return "{id}";
        }
        return segment;
    }

    private static boolean isIdentityKey(String key) {
        return key.contains("customerid")
                || key.contains("requestedby")
                || key.contains("username")
                || key.equals("principal");
    }

    private static boolean isIpKey(String key) {
        return key.equals("remote")
                || key.endsWith("ip")
                || key.contains("remoteaddr");
    }

    private static boolean isPathKey(String key) {
        return key.equals("path")
                || key.endsWith("path")
                || key.contains("uri");
    }

    private static boolean isTokenKey(String key) {
        return key.contains("token") || key.contains("authorization");
    }
}
