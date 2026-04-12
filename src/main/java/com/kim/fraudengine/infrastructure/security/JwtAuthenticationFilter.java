package com.kim.fraudengine.infrastructure.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final Logger securityLog = LoggerFactory.getLogger("com.capitec.fraud.security.events");
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserDetailsChecker userDetailsChecker = new AccountStatusUserDetailsChecker();

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            String username = jwtService.extractUsername(token);

            if (username == null || username.isBlank()) {
                rejectUnauthorized(response, "missing_username_claim", null);
                return;
            }

            var userDetails = userDetailsService.loadUserByUsername(username);
            userDetailsChecker.check(userDetails);

            if (!jwtService.isTokenValid(token, userDetails.getUsername())) {
                securityLog.warn("event=jwt_validation_failed reason=invalid_signature_or_claims");
                commenceUnauthorized(response);
                return;
            }

            String customerIdFromToken = jwtService.extractCustomerId(token);
            if (!isCustomerScopeValid(userDetails, customerIdFromToken)) {
                securityLog.warn("event=jwt_customer_scope_mismatch reason=customer_scope_mismatch");
                commenceUnauthorized(response);
                return;
            }

            var principal = new AuthenticatedRequestPrincipal(
                    username,
                    resolveCurrentCustomerId(userDetails));

            var authToken = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    userDetails.getAuthorities());

            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("Authenticated JWT principal for current request");

        } catch (AuthenticationException e) {
            rejectUnauthorized(response, authenticationFailureReason(e), null);
            return;
        } catch (Exception e) {
            rejectUnauthorized(response, "unexpected_exception", e);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isCustomerScopeValid(
            org.springframework.security.core.userdetails.UserDetails userDetails,
            String customerIdFromToken) {
        if (userDetails instanceof CustomerScopedPrincipal customerScopedPrincipal) {
            return Objects.equals(
                    normalizeCustomerId(customerScopedPrincipal.customerId()),
                    normalizeCustomerId(customerIdFromToken));
        }
        return true;
    }

    private String resolveCurrentCustomerId(
            org.springframework.security.core.userdetails.UserDetails userDetails) {
        if (userDetails instanceof CustomerScopedPrincipal customerScopedPrincipal) {
            return customerScopedPrincipal.customerId();
        }
        return null;
    }

    private String authenticationFailureReason(AuthenticationException exception) {
        if (exception instanceof BadCredentialsException) {
            return "bad_credentials";
        }
        if (exception instanceof DisabledException) {
            return "account_disabled";
        }
        if (exception instanceof LockedException) {
            return "account_locked";
        }
        if (exception instanceof AccountExpiredException) {
            return "account_expired";
        }
        if (exception instanceof CredentialsExpiredException) {
            return "credentials_expired";
        }
        return "authentication_exception";
    }

    private String normalizeCustomerId(String customerId) {
        if (customerId == null) {
            return null;
        }
        String normalized = customerId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    @SuppressFBWarnings(
            value = "CRLF_INJECTION_LOGS",
            justification = "reason is a fixed internal constant from authenticationFailureReason(); exception class name contains only safe characters")
    private void rejectUnauthorized(
            HttpServletResponse response,
            String reason,
            Exception exception) throws IOException {
        if (exception == null) {
            securityLog.warn("event=jwt_validation_failed reason={}", reason);
        } else {
            securityLog.warn(
                    "event=jwt_validation_failed reason={} errorType={}",
                    reason,
                    exception.getClass().getSimpleName());
        }
        commenceUnauthorized(response);
    }

    private void commenceUnauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Unauthorized\"}");
    }
}