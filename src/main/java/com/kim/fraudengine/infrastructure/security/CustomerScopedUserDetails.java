package com.kim.fraudengine.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class CustomerScopedUserDetails extends User implements CustomerScopedPrincipal {

    private final String customerId;

    public CustomerScopedUserDetails(String username,
                                     String password,
                                     Collection<? extends GrantedAuthority> authorities,
                                     String customerId) {
        super(username, password, authorities);
        this.customerId = normalize(customerId);
    }

    @Override
    public String customerId() {
        return customerId;
    }

    private static String normalize(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return null;
        }
        return customerId;
    }
}
