package com.kim.fraudengine.infrastructure.config;

import com.kim.fraudengine.infrastructure.logging.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void cors_configuration_uses_explicit_origin_allowlist() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(
                new CorsProperties(List.of("https://fraud.capitec.example")));

        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/api/v1/alerts");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://fraud.capitec.example");
        assertThat(configuration.getAllowedMethods()).containsExactly(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.OPTIONS.name());
        assertThat(configuration.getAllowedHeaders()).containsExactly(
                "Authorization",
                "Content-Type",
                RequestCorrelationFilter.CORRELATION_HEADER);
        assertThat(configuration.getExposedHeaders()).containsExactly(RequestCorrelationFilter.CORRELATION_HEADER);
    }

    @Test
    void cors_configuration_defaults_to_no_browser_origins() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(new CorsProperties(List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/api/v1/alerts");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).isEmpty();
    }
}
