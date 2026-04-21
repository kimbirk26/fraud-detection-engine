package com.kim.fraudengine.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;

    @Mock private UserDetailsService userDetailsService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_returns401_whenCurrentUserIsDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer signed.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        when(jwtService.extractUsername("signed.jwt.token")).thenReturn("analyst");
        when(userDetailsService.loadUserByUsername("analyst"))
                .thenReturn(
                        new CustomerScopedUserDetails(
                                "analyst",
                                "{argon2}encoded",
                                false,
                                true,
                                true,
                                true,
                                List.of(new SimpleGrantedAuthority("alerts:read:all")),
                                null));

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"Unauthorized\"}");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_usesCurrentAuthoritiesFromUserStore() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer signed.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();
        CustomerScopedUserDetails currentUser =
                new CustomerScopedUserDetails(
                        "customer_cust001",
                        "{argon2}encoded",
                        List.of(new SimpleGrantedAuthority("alerts:read")),
                        "CUST001");

        when(jwtService.extractUsername("signed.jwt.token")).thenReturn("customer_cust001");
        when(userDetailsService.loadUserByUsername("customer_cust001")).thenReturn(currentUser);
        when(jwtService.isTokenValid("signed.jwt.token", "customer_cust001")).thenReturn(true);
        when(jwtService.extractCustomerId("signed.jwt.token")).thenReturn("CUST001");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isTrue();
        assertThat(filterChain.authentication).isNotNull();
        assertThat(filterChain.authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("alerts:read");
        assertThat(filterChain.authentication.getPrincipal())
                .isEqualTo(new AuthenticatedRequestPrincipal("customer_cust001", "CUST001"));
        verify(jwtService, never()).extractRoles(anyString());
    }

    private static final class RecordingFilterChain implements FilterChain {
        private boolean invoked;
        private Authentication authentication;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            invoked = true;
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
    }
}
