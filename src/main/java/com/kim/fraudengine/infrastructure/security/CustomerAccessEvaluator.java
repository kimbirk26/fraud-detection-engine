package com.kim.fraudengine.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component("customerAccess")
public class CustomerAccessEvaluator {

    public boolean canRead(String customerId, Authentication authentication) {
        if (customerId == null || customerId.isBlank() ||
            authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean hasBroadReadAccess = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority ->
                        authority.equals("alerts:read:all") || authority.equals("ROLE_ADMIN"));

        if (hasBroadReadAccess) {
            return true;
        }

        if (authentication.getPrincipal() instanceof CustomerScopedPrincipal customerScopedPrincipal) {
            String principalCustomerId = customerScopedPrincipal.customerId();
            return principalCustomerId != null && customerId.equalsIgnoreCase(principalCustomerId);
        }

        return false;
    }
}
