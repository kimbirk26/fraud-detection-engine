package com.kim.fraudengine.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthBootstrapRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JdbcCustomerScopedUserDetailsService userDetailsService;

    private AuthBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        AuthBootstrapProperties properties = new AuthBootstrapProperties(
                true,
                List.of(new AuthBootstrapProperties.BootstrapUser(
                        "analyst",
                        "analyst_pass",
                        null,
                        List.of("ROLE_USER", "alerts:read:all"),
                        true,
                        true,
                        true,
                        true)));
        runner = new AuthBootstrapRunner(
                properties,
                jdbcTemplate,
                passwordEncoder,
                userDetailsService,
                new ImmediateTransactionOperations());
    }

    @Test
    void run_createsMissingConfiguredUser() throws Exception {
        when(userDetailsService.loadUserByUsername("analyst"))
                .thenThrow(new UsernameNotFoundException("missing"));
        when(passwordEncoder.encode("analyst_pass")).thenReturn("{argon2}encoded");

        runner.run(null);

        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("insert into auth_users")),
                eq("analyst"),
                eq("{argon2}encoded"),
                eq(null),
                eq(true),
                eq(true),
                eq(true),
                eq(true));
        verify(jdbcTemplate).update("delete from auth_user_authorities where username = ?", "analyst");
        verify(jdbcTemplate, times(2)).update(
                argThat(sql -> sql.contains("insert into auth_user_authorities")),
                eq("analyst"),
                anyString());
    }

    @Test
    void run_skipsUnchangedConfiguredUser() throws Exception {
        when(userDetailsService.loadUserByUsername("analyst"))
                .thenReturn(new CustomerScopedUserDetails(
                        "analyst",
                        "{argon2}encoded",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority("alerts:read:all")),
                        null));
        when(passwordEncoder.matches("analyst_pass", "{argon2}encoded")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("{argon2}encoded")).thenReturn(false);

        runner.run(null);

        verifyNoInteractions(jdbcTemplate);
        verify(passwordEncoder, never()).encode("analyst_pass");
    }

    private static final class ImmediateTransactionOperations implements TransactionOperations {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
