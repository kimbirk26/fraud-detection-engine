package com.kim.fraudengine.infrastructure.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JdbcCustomerScopedUserDetailsService
        implements UserDetailsService, UserDetailsPasswordService {

    private static final String LOAD_USER_SQL =
            """
            select username, password_hash, customer_id, enabled,
                   account_non_locked, account_non_expired, credentials_non_expired
            from auth_users
            where username = ?
            """;
    private static final String LOAD_AUTHORITIES_SQL =
            """
            select authority
            from auth_user_authorities
            where username = ?
            order by authority
            """;
    private static final String UPDATE_PASSWORD_SQL =
            """
            update auth_users
            set password_hash = ?, updated_at = current_timestamp
            where username = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Spring-managed singleton - effectively immutable after context initialization")
    public JdbcCustomerScopedUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Username must not be blank");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(LOAD_USER_SQL, username);
        if (rows.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        if (rows.size() > 1) {
            throw new IllegalStateException("Multiple auth users found for username: " + username);
        }

        Map<String, Object> row = rows.getFirst();
        List<SimpleGrantedAuthority> authorities =
                jdbcTemplate.queryForList(LOAD_AUTHORITIES_SQL, String.class, username).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        return new CustomerScopedUserDetails(
                requiredString(row, "username"),
                requiredString(row, "password_hash"),
                requiredBoolean(row, "enabled"),
                requiredBoolean(row, "account_non_expired"),
                requiredBoolean(row, "credentials_non_expired"),
                requiredBoolean(row, "account_non_locked"),
                authorities,
                optionalString(row, "customer_id"));
    }

    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        int updated = jdbcTemplate.update(UPDATE_PASSWORD_SQL, newPassword, user.getUsername());
        if (updated != 1) {
            throw new UsernameNotFoundException("User not found: " + user.getUsername());
        }

        String customerId =
                user instanceof CustomerScopedPrincipal customerScopedPrincipal
                        ? customerScopedPrincipal.customerId()
                        : null;

        return new CustomerScopedUserDetails(
                user.getUsername(),
                newPassword,
                user.isEnabled(),
                user.isAccountNonExpired(),
                user.isCredentialsNonExpired(),
                user.isAccountNonLocked(),
                user.getAuthorities(),
                customerId);
    }

    private String requiredString(Map<String, Object> row, String column) {
        String value = optionalString(row, column);
        if (value == null) {
            throw new IllegalStateException("Missing required auth column: " + column);
        }
        return value;
    }

    private String optionalString(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean requiredBoolean(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalStateException("Missing required auth column: " + column);
    }
}
