package com.kim.fraudengine.infrastructure.security;

import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class CustomerScopedUserDetails extends User implements CustomerScopedPrincipal {

    @Serial private static final long serialVersionUID = 1L;

    private final String customerId;

    public CustomerScopedUserDetails(
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            String customerId) {
        this(username, password, true, true, true, true, authorities, customerId);
    }

    public CustomerScopedUserDetails(
            String username,
            String password,
            boolean enabled,
            boolean accountNonExpired,
            boolean credentialsNonExpired,
            boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities,
            String customerId) {
        super(
                username,
                password,
                enabled,
                accountNonExpired,
                credentialsNonExpired,
                accountNonLocked,
                authorities);
        this.customerId = normalize(customerId);
    }

    @Override
    public String customerId() {
        return customerId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CustomerScopedUserDetails other)) return false;
        if (!super.equals(other)) return false;
        return Objects.equals(customerId, other.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), customerId);
    }

    private static String normalize(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return null;
        }
        return customerId;
    }
}
