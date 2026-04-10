package com.kim.fraudengine.adapter.rest.auth;

import com.kim.fraudengine.adapter.rest.dto.TokenRequest;
import com.kim.fraudengine.adapter.rest.dto.TokenResponse;
import com.kim.fraudengine.infrastructure.security.CustomerScopedPrincipal;
import com.kim.fraudengine.infrastructure.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Issues JWT tokens in exchange for valid credentials.
 * <p>
 * This is the only endpoint that does not require an existing token.
 * All other endpoints require "Authorization: Bearer <token>".
 * <p>
 * In production: consider rate-limiting this endpoint to prevent
 * brute-force attacks. Spring Security's built-in lockout or a
 * Bucket4j rate limiter work well here.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger securityLog =
            LoggerFactory.getLogger("com.capitec.fraud.security.events");

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final long expiryMinutes;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          @Value("${app.jwt.expiry-minutes:60}") long expiryMinutes) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.expiryMinutes = expiryMinutes;
    }

    /**
     * Exchange username + password for a signed JWT.
     * <p>
     * 200 with TokenResponse on success.
     * 401 on bad credentials — deliberately vague ("invalid credentials")
     * to avoid confirming whether the username exists.
     */
    @PostMapping("/token")
    public ResponseEntity<?> token(@Valid @RequestBody TokenRequest request,
                                   HttpServletRequest httpRequest) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));

            List<String> roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            String customerId = extractCustomerId(auth.getPrincipal());
            String token = customerId == null
                    ? jwtService.generateToken(auth.getName(), roles)
                    : jwtService.generateToken(auth.getName(), roles, customerId);
            return ResponseEntity.ok(TokenResponse.bearer(token, expiryMinutes));

        } catch (BadCredentialsException e) {
            securityLog.warn("event=login_failure username={} path={} remote={} reason=bad_credentials",
                    request.username(),
                    httpRequest.getRequestURI(),
                    httpRequest.getRemoteAddr());
            // Return 401, not 403 — the request is valid but credentials are wrong
            // Return the same message whether username or password is wrong —
            // prevents username enumeration attacks
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorBody("Invalid credentials"));
        }
    }

    private String extractCustomerId(Object principal) {
        if (principal instanceof CustomerScopedPrincipal customerScopedPrincipal) {
            return customerScopedPrincipal.customerId();
        }
        return null;
    }

    // Simple inline record — only used for the error case in this controller
    private record ErrorBody(String message) {
    }
}
