package com.kim.fraudengine.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final Logger securityLog = LoggerFactory.getLogger("com.capitec.fraud.security.events");
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                var userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, userDetails.getUsername())) {
                    String customerId = jwtService.extractCustomerId(token);
                    if (!isCustomerScopeValid(userDetails, customerId)) {
                        securityLog.warn("event=jwt_customer_scope_mismatch username={} path={} remote={} customerId={}", username, request.getRequestURI(), request.getRemoteAddr(), customerId);
                        commenceUnauthorized(response, new BadCredentialsException("Invalid JWT customer scope"));
                        return;
                    }

                    List<SimpleGrantedAuthority> authorities = jwtService.extractRoles(token).stream().map(SimpleGrantedAuthority::new).toList();

                    var principal = new AuthenticatedRequestPrincipal(username, customerId);
                    var authToken = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user '{}' via JWT", username);
                } else {
                    securityLog.warn("event=jwt_validation_failed path={} remote={} reason=invalid_signature_or_claims", request.getRequestURI(), request.getRemoteAddr());
                    commenceUnauthorized(response, new BadCredentialsException("Invalid JWT"));
                    return;
                }
            }
        } catch (Exception e) {
            securityLog.warn("event=jwt_validation_failed path={} remote={} reason={}", request.getRequestURI(), request.getRemoteAddr(), e.getMessage());

            SecurityContextHolder.clearContext();
            commenceUnauthorized(response, new BadCredentialsException("Invalid JWT", e));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isCustomerScopeValid(org.springframework.security.core.userdetails.UserDetails userDetails, String customerIdFromToken) {
        if (userDetails instanceof CustomerScopedPrincipal customerScopedPrincipal) {
            String expectedCustomerId = customerScopedPrincipal.customerId();
            if (expectedCustomerId == null) {
                return customerIdFromToken == null;
            }
            return expectedCustomerId.equalsIgnoreCase(customerIdFromToken);
        }
        return true;
    }

    private void commenceUnauthorized(HttpServletResponse response, BadCredentialsException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Unauthorized\"}");
    }
}
