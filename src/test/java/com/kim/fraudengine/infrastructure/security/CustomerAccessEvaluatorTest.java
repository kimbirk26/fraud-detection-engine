package com.kim.fraudengine.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CustomerAccessEvaluatorTest {

    private final CustomerAccessEvaluator evaluator = new CustomerAccessEvaluator();

    // --- canRead ---

    @Test
    void canRead_returnsFalse_whenCustomerIdIsNull() {
        Authentication auth = authenticatedUser("ROLE_ADMIN");
        assertThat(evaluator.canRead(null, auth)).isFalse();
    }

    @Test
    void canRead_returnsFalse_whenCustomerIdIsBlank() {
        Authentication auth = authenticatedUser("ROLE_ADMIN");
        assertThat(evaluator.canRead("  ", auth)).isFalse();
    }

    @Test
    void canRead_returnsFalse_whenAuthenticationIsNull() {
        assertThat(evaluator.canRead("CUST001", null)).isFalse();
    }

    @Test
    void canRead_returnsTrue_whenUserHasAlertsReadAll() {
        Authentication auth = authenticatedUser("alerts:read:all");
        assertThat(evaluator.canRead("CUST001", auth)).isTrue();
    }

    @Test
    void canRead_returnsTrue_whenUserHasRoleAdmin() {
        Authentication auth = authenticatedUser("ROLE_ADMIN");
        assertThat(evaluator.canRead("CUST001", auth)).isTrue();
    }

    @Test
    void canRead_returnsTrue_whenCustomerScopedPrincipalMatches() {
        Authentication auth = customerScopedAuth("CUST001", "transactions:write");
        assertThat(evaluator.canRead("CUST001", auth)).isTrue();
    }

    @Test
    void canRead_returnsFalse_whenCustomerScopedPrincipalDoesNotMatch() {
        Authentication auth = customerScopedAuth("CUST002", "transactions:write");
        assertThat(evaluator.canRead("CUST001", auth)).isFalse();
    }

    @Test
    void canRead_matchesCaseInsensitively() {
        Authentication auth = customerScopedAuth("cust001", "transactions:write");
        assertThat(evaluator.canRead("CUST001", auth)).isTrue();
    }

    // --- canWrite ---

    @Test
    void canWrite_returnsFalse_whenCustomerIdIsNull() {
        Authentication auth = authenticatedUser("ROLE_ADMIN");
        assertThat(evaluator.canWrite(null, auth)).isFalse();
    }

    @Test
    void canWrite_returnsFalse_whenCustomerIdIsBlank() {
        Authentication auth = authenticatedUser("ROLE_ADMIN");
        assertThat(evaluator.canWrite("  ", auth)).isFalse();
    }

    @Test
    void canWrite_returnsFalse_whenAuthenticationIsNull() {
        assertThat(evaluator.canWrite("CUST001", null)).isFalse();
    }

    @Test
    void canWrite_returnsTrue_whenCustomerScopedPrincipalHasNullCustomerId() {
        // Analyst/admin with null customerId — broad access
        Authentication auth = customerScopedAuth(null, "transactions:write");
        assertThat(evaluator.canWrite("CUST001", auth)).isTrue();
    }

    @Test
    void canWrite_returnsTrue_whenCustomerScopedPrincipalMatches() {
        Authentication auth = customerScopedAuth("CUST001", "transactions:write");
        assertThat(evaluator.canWrite("CUST001", auth)).isTrue();
    }

    @Test
    void canWrite_returnsFalse_whenCustomerScopedPrincipalDoesNotMatch() {
        Authentication auth = customerScopedAuth("CUST002", "transactions:write");
        assertThat(evaluator.canWrite("CUST001", auth)).isFalse();
    }

    @Test
    void canWrite_returnsTrue_whenRoleAdmin() {
        Authentication auth = authenticatedUser("ROLE_ADMIN");
        assertThat(evaluator.canWrite("CUST001", auth)).isTrue();
    }

    @Test
    void canWrite_returnsFalse_whenNoMatchingRoleOrPrincipal() {
        Authentication auth = authenticatedUser("transactions:write");
        assertThat(evaluator.canWrite("CUST001", auth)).isFalse();
    }

    @Test
    void canWrite_matchesCaseInsensitively() {
        Authentication auth = customerScopedAuth("cust001", "transactions:write");
        assertThat(evaluator.canWrite("CUST001", auth)).isTrue();
    }

    // --- helpers ---

    private Authentication authenticatedUser(String... authorities) {
        var grantedAuthorities =
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList();
        TestingAuthenticationToken token =
                new TestingAuthenticationToken("user", "password", List.copyOf(grantedAuthorities));
        token.setAuthenticated(true);
        return token;
    }

    private Authentication customerScopedAuth(String customerId, String... authorities) {
        CustomerScopedPrincipal principal = () -> customerId;
        var grantedAuthorities =
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList();
        TestingAuthenticationToken token =
                new TestingAuthenticationToken(principal, null, List.copyOf(grantedAuthorities));
        token.setAuthenticated(true);
        return token;
    }
}
