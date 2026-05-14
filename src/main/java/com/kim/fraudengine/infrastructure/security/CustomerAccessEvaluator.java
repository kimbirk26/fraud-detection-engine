package com.kim.fraudengine.infrastructure.security;

import java.util.Locale;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component("customerAccess")
public class CustomerAccessEvaluator {

    public boolean canRead(String customerId, Authentication authentication) {
        if (customerId == null
                || customerId.isBlank()
                || authentication == null
                || !authentication.isAuthenticated()) {
            return false;
        }

        boolean hasBroadReadAccess =
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(
                                authority ->
                                        authority.equals("alerts:read:all")
                                                || authority.equals("ROLE_ADMIN"));

        if (hasBroadReadAccess) {
            return true;
        }

        if (authentication.getPrincipal()
                instanceof CustomerScopedPrincipal customerScopedPrincipal) {
            return Objects.equals(
                    normalizeCustomerId(customerId),
                    normalizeCustomerId(customerScopedPrincipal.customerId()));
        }

        return false;
    }

    public boolean canWrite(String customerId, Authentication authentication) {
        if (customerId == null
                || customerId.isBlank()
                || authentication == null
                || !authentication.isAuthenticated()) {
            return false;
        }

        if (authentication.getPrincipal()
                instanceof CustomerScopedPrincipal customerScopedPrincipal) {
            if (customerScopedPrincipal.customerId() == null) {
                return true;
            }
            return Objects.equals(
                    normalizeCustomerId(customerId),
                    normalizeCustomerId(customerScopedPrincipal.customerId()));
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    /**
     * Returns the authoritative customer ID for a write operation.
     *
     * <p>For customer-scoped principals the ID is derived from the authenticated token,
     * not from the request body. This ensures that even if the {@code @PreAuthorize}
     * gate is bypassed or misconfigured, the domain event cannot be attributed to
     * a different customer.
     *
     * <p>For broad-access principals (analysts/admins whose token carries no customer
     * scope) the caller-supplied {@code requestCustomerId} is returned as-is.
     */
    public String resolveCustomerId(String requestCustomerId, Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal()
                        instanceof CustomerScopedPrincipal customerScopedPrincipal
                && customerScopedPrincipal.customerId() != null) {
            return customerScopedPrincipal.customerId();
        }
        return requestCustomerId;
    }

    private String normalizeCustomerId(String customerId) {
        if (customerId == null) {
            return null;
        }

        String normalized = customerId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }
}
