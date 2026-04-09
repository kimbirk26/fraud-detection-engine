package com.kim.fraudengine.infrastructure.config;

import com.kim.fraudengine.infrastructure.security.CustomerScopedUserDetails;
import com.kim.fraudengine.infrastructure.security.JwtAuthenticationFilter;
import com.kim.fraudengine.infrastructure.security.MigrationAwarePasswordEncoder;
import com.kim.fraudengine.infrastructure.security.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger securityLog =
            LoggerFactory.getLogger("com.capitec.fraud.security.events");

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            securityLog.warn("event=authentication_required path={} remote={} reason={}",
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    authException.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Unauthorized\"}");
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            securityLog.warn("event=access_denied principal={} path={} remote={} reason={}",
                    currentPrincipalName(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    accessDeniedException.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Forbidden\"}");
        };
    }

    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilter() {
        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestCorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationFilter jwtAuthenticationFilter,
                                    AuthenticationEntryPoint authenticationEntryPoint,
                                    AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/token").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var manager = new InMemoryUserDetailsManager();
        manager.createUser(user(
                "analyst",
                encoder.encode("analyst_pass"),
                null,
                "ROLE_USER",
                "transactions:write",
                "alerts:read",
                "alerts:read:all"));
        manager.createUser(user(
                "admin",
                encoder.encode("admin_pass"),
                null,
                "ROLE_USER",
                "ROLE_ADMIN",
                "transactions:write",
                "alerts:read",
                "alerts:read:all"));
        manager.createUser(user(
                "customer_cust001",
                encoder.encode("customer_pass"),
                "CUST001",
                "ROLE_USER",
                "alerts:read"));
        return manager;
    }

    /**
     * Argon2id for new hashes, with bcrypt support for migration.
     * This keeps legacy passwords working while making Argon2id the default
     * for all newly encoded or upgraded hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new MigrationAwarePasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider wires together UserDetailsService and
     * PasswordEncoder — Spring uses this to validate login credentials.
     * When the user store supports it, successful logins can transparently
     * upgrade legacy hashes to the current encoder.
     */
    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        if (userDetailsService instanceof UserDetailsPasswordService passwordService) {
            provider.setUserDetailsPasswordService(passwordService);
        }
        return provider;
    }

    /**
     * Exposes AuthenticationManager as a bean so AuthController can inject it.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private CustomerScopedUserDetails user(String username,
                                           String password,
                                           String customerId,
                                           String... authorities) {
        return new CustomerScopedUserDetails(
                username,
                password,
                Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList(),
                customerId);
    }

    private String currentPrincipalName() {
        Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext()
                        .getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
