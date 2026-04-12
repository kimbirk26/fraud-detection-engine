package com.kim.fraudengine.infrastructure.security;

import com.kim.fraudengine.infrastructure.logging.SensitiveLogValueSanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "app.auth.bootstrap", name = "enabled", havingValue = "true")
public class AuthBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrapRunner.class);

    private static final String UPSERT_USER_SQL = """
            insert into auth_users (
                username,
                password_hash,
                customer_id,
                enabled,
                account_non_locked,
                account_non_expired,
                credentials_non_expired
            )
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (username) do update set
                password_hash = excluded.password_hash,
                customer_id = excluded.customer_id,
                enabled = excluded.enabled,
                account_non_locked = excluded.account_non_locked,
                account_non_expired = excluded.account_non_expired,
                credentials_non_expired = excluded.credentials_non_expired,
                updated_at = current_timestamp
            """;
    private static final String DELETE_AUTHORITIES_SQL =
            "delete from auth_user_authorities where username = ?";
    private static final String INSERT_AUTHORITY_SQL = """
            insert into auth_user_authorities (username, authority)
            values (?, ?)
            on conflict (username, authority) do nothing
            """;

    private final AuthBootstrapProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JdbcCustomerScopedUserDetailsService userDetailsService;
    private final TransactionOperations transactionOperations;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "Spring-managed singletons - effectively immutable after context initialization")
    public AuthBootstrapRunner(
            AuthBootstrapProperties properties,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JdbcCustomerScopedUserDetailsService userDetailsService,
            TransactionOperations transactionOperations) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.users().isEmpty()) {
            log.warn("Auth bootstrap is enabled but no users are configured");
            return;
        }

        transactionOperations.executeWithoutResult(status ->
                properties.users().forEach(this::synchroniseUser));
    }

    private void synchroniseUser(AuthBootstrapProperties.BootstrapUser configuredUser) {
        CustomerScopedUserDetails existingUser = findExistingUser(configuredUser.username());

        String desiredCustomerId = normalize(configuredUser.customerId());
        String passwordHash = resolvePasswordHash(configuredUser, existingUser);
        boolean userChanged = existingUser == null
                || !Objects.equals(existingUser.customerId(), desiredCustomerId)
                || existingUser.isEnabled() != configuredUser.enabledFlag()
                || existingUser.isAccountNonLocked() != configuredUser.accountNonLockedFlag()
                || existingUser.isAccountNonExpired() != configuredUser.accountNonExpiredFlag()
                || existingUser.isCredentialsNonExpired() != configuredUser.credentialsNonExpiredFlag()
                || !Objects.equals(existingUser.getPassword(), passwordHash);
        boolean authoritiesChanged = existingUser == null
                || !sameAuthorities(existingUser.getAuthorities(), configuredUser.authorities());

        if (userChanged) {
            jdbcTemplate.update(
                    UPSERT_USER_SQL,
                    configuredUser.username(),
                    passwordHash,
                    desiredCustomerId,
                    configuredUser.enabledFlag(),
                    configuredUser.accountNonLockedFlag(),
                    configuredUser.accountNonExpiredFlag(),
                    configuredUser.credentialsNonExpiredFlag());
        }

        if (authoritiesChanged) {
            jdbcTemplate.update(DELETE_AUTHORITIES_SQL, configuredUser.username());
            configuredUser.authorities()
                    .forEach(authority ->
                            jdbcTemplate.update(INSERT_AUTHORITY_SQL, configuredUser.username(), authority));
        }

        log.info(
                "Auth bootstrap {} user {}",
                existingUser == null ? "created" : (userChanged || authoritiesChanged ? "updated" : "verified"),
                SensitiveLogValueSanitizer.normalizeForLog(configuredUser.username()));
    }

    private CustomerScopedUserDetails findExistingUser(String username) {
        try {
            return (CustomerScopedUserDetails) userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException ex) {
            return null;
        }
    }

    private String resolvePasswordHash(
            AuthBootstrapProperties.BootstrapUser configuredUser,
            CustomerScopedUserDetails existingUser) {
        if (existingUser == null) {
            return passwordEncoder.encode(configuredUser.password());
        }

        if (passwordEncoder.matches(configuredUser.password(), existingUser.getPassword())
                && !passwordEncoder.upgradeEncoding(existingUser.getPassword())) {
            return existingUser.getPassword();
        }

        return passwordEncoder.encode(configuredUser.password());
    }

    private boolean sameAuthorities(
            Collection<?> existingAuthorities,
            List<String> configuredAuthorities) {
        List<String> current = existingAuthorities.stream()
                .map(authority -> authority instanceof GrantedAuthority grantedAuthority
                        ? grantedAuthority.getAuthority()
                        : authority.toString())
                .sorted()
                .toList();
        List<String> configured = configuredAuthorities.stream()
                .sorted()
                .toList();
        return current.equals(configured);
    }

    private String normalize(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return null;
        }
        return customerId.trim();
    }
}
