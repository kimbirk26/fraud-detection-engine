package com.kim.fraudengine.infrastructure.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.bootstrap")
public record AuthBootstrapProperties(boolean enabled, List<@Valid BootstrapUser> users) {

    public AuthBootstrapProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record BootstrapUser(
            @NotBlank String username,
            @NotBlank String password,
            String customerId,
            @NotEmpty List<@NotBlank String> authorities,
            Boolean enabled,
            Boolean accountNonLocked,
            Boolean accountNonExpired,
            Boolean credentialsNonExpired) {

        public BootstrapUser {
            authorities = authorities == null ? List.of() : List.copyOf(authorities);
        }

        public boolean enabledFlag() {
            return enabled == null || enabled;
        }

        public boolean accountNonLockedFlag() {
            return accountNonLocked == null || accountNonLocked;
        }

        public boolean accountNonExpiredFlag() {
            return accountNonExpired == null || accountNonExpired;
        }

        public boolean credentialsNonExpiredFlag() {
            return credentialsNonExpired == null || credentialsNonExpired;
        }
    }
}
