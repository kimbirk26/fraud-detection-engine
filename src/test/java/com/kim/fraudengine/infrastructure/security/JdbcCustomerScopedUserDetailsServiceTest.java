package com.kim.fraudengine.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class JdbcCustomerScopedUserDetailsServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private JdbcCustomerScopedUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new JdbcCustomerScopedUserDetailsService(jdbcTemplate);
    }

    @Test
    void loadUserByUsername_returnsCustomerScopedUser() {
        when(jdbcTemplate.queryForList(anyString(), eq("customer_cust001")))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "username", "customer_cust001",
                                        "password_hash", "{argon2}encoded",
                                        "customer_id", "CUST001",
                                        "enabled", true,
                                        "account_non_locked", true,
                                        "account_non_expired", true,
                                        "credentials_non_expired", true)));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("customer_cust001")))
                .thenReturn(List.of("alerts:read"));

        UserDetails user = service.loadUserByUsername("customer_cust001");

        assertThat(user).isInstanceOf(CustomerScopedUserDetails.class);
        assertThat(user.getUsername()).isEqualTo("customer_cust001");
        assertThat(user.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("alerts:read");
        assertThat(((CustomerScopedPrincipal) user).customerId()).isEqualTo("CUST001");
    }

    @Test
    void loadUserByUsername_throwsWhenUserMissing() {
        when(jdbcTemplate.queryForList(anyString(), eq("missing-user"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.loadUserByUsername("missing-user"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing-user");
    }

    @Test
    void updatePassword_persistsHashAndReturnsUpdatedDetails() {
        UserDetails existingUser =
                new CustomerScopedUserDetails(
                        "analyst",
                        "{argon2}old",
                        List.of(new SimpleGrantedAuthority("alerts:read:all")),
                        null);
        when(jdbcTemplate.update(anyString(), eq("{argon2}new"), eq("analyst"))).thenReturn(1);

        UserDetails updated = service.updatePassword(existingUser, "{argon2}new");

        verify(jdbcTemplate).update(anyString(), eq("{argon2}new"), eq("analyst"));
        assertThat(updated.getPassword()).isEqualTo("{argon2}new");
        assertThat(updated.getUsername()).isEqualTo("analyst");
        assertThat(updated.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("alerts:read:all");
    }
}
