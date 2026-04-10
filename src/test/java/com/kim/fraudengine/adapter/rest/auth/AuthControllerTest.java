package com.kim.fraudengine.adapter.rest.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.rest.dto.TokenRequest;
import com.kim.fraudengine.infrastructure.security.JwtAuthenticationFilter;
import com.kim.fraudengine.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(AuthControllerTest.TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AuthenticationManager authenticationManager;

    @MockBean
    JwtService jwtService;

    @MockBean
    UserDetailsService userDetailsService;

    @Test
    void token_returns200_withValidCredentials() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "analyst", null,
                List.of(new SimpleGrantedAuthority("alerts:read")));
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateToken(any(), any())).thenReturn("signed.jwt.token");

        mockMvc.perform(post("/api/v1/auth/token").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenRequest("analyst", "analyst_pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void token_returns401_withBadCredentials() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/token").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenRequest("analyst", "wrong_pass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void token_returns400_withMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/auth/token").permitAll()
                            .anyRequest().authenticated())
                    .httpBasic(httpBasic -> httpBasic.disable())
                    .formLogin(form -> form.disable());
            return http.build();
        }
    }
}
